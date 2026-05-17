package com.orange.logistics.streaming.processor;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * 滑动窗口处理器
 * 实现基于时间窗口的异常聚合统计
 */
public class SlidingWindowProcessor {

    /**
     * 计算滑动窗口内的统计数据
     * 窗口大小: 30分钟，滑动步长: 5分钟
     */
    public static Dataset<Row> computeSlidingWindowStats(Dataset<Row> trackingEvents) {
        return trackingEvents
                .groupBy(
                        window(col("eventTime"), "30 minutes", "5 minutes"),
                        col("city"),
                        col("status")
                )
                .agg(
                        count("*").as("event_count"),
                        countDistinct("waybillNo").as("package_count"),
                        countDistinct("stationCode").as("station_count")
                )
                .withColumn("window_start", col("window.start"))
                .withColumn("window_end", col("window.end"))
                .drop("window");
    }

    /**
     * 计算异常率滑动窗口
     * 当窗口内异常数量超过阈值时触发告警
     */
    public static Dataset<Row> computeAnomalyRateWindow(Dataset<Row> anomalyEvents) {
        return anomalyEvents
                .groupBy(
                        window(col("detected_time"), "30 minutes", "5 minutes"),
                        col("stationCode"),
                        col("anomaly_type")
                )
                .agg(
                        count("*").as("anomaly_count"),
                        avg("severity").as("avg_severity"),
                        max("severity").as("max_severity")
                )
                .withColumn("window_start", col("window.start"))
                .withColumn("window_end", col("window.end"))
                .drop("window")
                .filter(col("anomaly_count").geq(5)); // 阈值: 30分钟内5次异常
    }
}
