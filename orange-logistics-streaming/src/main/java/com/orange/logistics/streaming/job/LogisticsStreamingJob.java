package com.orange.logistics.streaming.job;

import com.orange.logistics.streaming.processor.AnomalyDetectionProcessor;
import com.orange.logistics.streaming.processor.SlidingWindowProcessor;
import com.orange.logistics.streaming.sink.ElasticsearchSink;
import com.orange.logistics.streaming.sink.ClickHouseSink;
import com.orange.logistics.streaming.sink.MySQLSink;
import com.orange.logistics.streaming.sink.RedisSink;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import org.apache.spark.sql.types.*;

import java.util.concurrent.TimeUnit;

import static org.apache.spark.sql.functions.*;

/**
 * 物流实时流处理主任务
 * 从 Kafka 消费物流轨迹数据，进行实时异常检测和分析
 */
public class LogisticsStreamingJob {

    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getProperty("kafka.bootstrap.servers", "kafka:9092");
    private static final String KAFKA_TOPIC = "logistics-tracking-events";

    public static void main(String[] args) throws Exception {
        SparkSession spark = SparkSession.builder()
                .appName("orange-logistics-Streaming")
                .config("spark.sql.streaming.checkpointLocation", "/tmp/checkpoint/logistics")
                .config("spark.sql.shuffle.partitions", "8")
                .config("spark.streaming.kafka.maxRatePerPartition", "1000")
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");

        // 定义轨迹事件 Schema
        StructType trackingSchema = new StructType()
                .add("waybillNo", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("status", DataTypes.StringType)
                .add("stationCode", DataTypes.StringType)
                .add("stationName", DataTypes.StringType)
                .add("city", DataTypes.StringType)
                .add("province", DataTypes.StringType)
                .add("latitude", DataTypes.DoubleType)
                .add("longitude", DataTypes.DoubleType)
                .add("operator", DataTypes.StringType)
                .add("remark", DataTypes.StringType)
                .add("eventTime", DataTypes.TimestampType);

        // 从 Kafka 读取数据
        Dataset<Row> kafkaStream = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS)
                .option("subscribe", KAFKA_TOPIC)
                .option("startingOffsets", "latest")
                .option("failOnDataLoss", "false")
                .load();

        // 解析 JSON 数据
        Dataset<Row> trackingEvents = kafkaStream
                .selectExpr("CAST(value AS STRING) as json_str")
                .select(from_json(col("json_str"), trackingSchema).as("data"))
                .select("data.*")
                .withColumn("processing_time", current_timestamp())
                .withWatermark("eventTime", "5 minutes");

        // 1. 异常检测 - 超时未更新检测
        Dataset<Row> timeoutAnomalies = AnomalyDetectionProcessor.detectTimeoutAnomalies(trackingEvents);

        // 2. 路由回环检测
        Dataset<Row> routeLoopAnomalies = AnomalyDetectionProcessor.detectRouteLoops(trackingEvents);

        // 3. 滑动窗口异常聚合
        Dataset<Row> windowedAnomalies = SlidingWindowProcessor.computeSlidingWindowStats(trackingEvents);

        // 4. 写入 Elasticsearch（轨迹检索）
        StreamingQuery esQuery = trackingEvents.writeStream()
                .foreachBatch((batchDF, batchId) -> {
                    if (!batchDF.isEmpty()) {
                        ElasticsearchSink.writeBatch(batchDF, batchId);
                    }
                })
                .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .option("checkpointLocation", "/tmp/checkpoint/es-tracking")
                .start();

        // 5. 写入 ClickHouse（OLAP 分析）
        StreamingQuery chQuery = trackingEvents.writeStream()
                .foreachBatch((batchDF, batchId) -> {
                    if (!batchDF.isEmpty()) {
                        ClickHouseSink.writeBatch(batchDF, batchId);
                    }
                })
                .trigger(Trigger.ProcessingTime(30, TimeUnit.SECONDS))
                .option("checkpointLocation", "/tmp/checkpoint/ch-tracking")
                .start();

        // 6. 异常结果写入 MySQL + Redis + Kafka
        StreamingQuery anomalyQuery = timeoutAnomalies.union(routeLoopAnomalies)
                .writeStream()
                .foreachBatch((batchDF, batchId) -> {
                    if (!batchDF.isEmpty()) {
                        MySQLSink.writeAnomalies(batchDF, batchId);
                        RedisSink.updateAnomalyStatus(batchDF, batchId);
                    }
                })
                .trigger(Trigger.ProcessingTime(5, TimeUnit.SECONDS))
                .option("checkpointLocation", "/tmp/checkpoint/anomaly")
                .start();

        // 7. 滑动窗口统计写入 Redis
        StreamingQuery windowQuery = windowedAnomalies.writeStream()
                .foreachBatch((batchDF, batchId) -> {
                    if (!batchDF.isEmpty()) {
                        RedisSink.updateWindowStats(batchDF, batchId);
                    }
                })
                .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .option("checkpointLocation", "/tmp/checkpoint/window-stats")
                .start();

        spark.streams().awaitAnyTermination();
    }
}
