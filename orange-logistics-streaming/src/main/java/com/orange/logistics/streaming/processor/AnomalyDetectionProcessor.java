package com.orange.logistics.streaming.processor;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * 异常检测处理器
 * 实现超时检测和路由回环检测
 */
public class AnomalyDetectionProcessor {

    // 各状态超时阈值（分钟）
    private static final long PICKUP_TIMEOUT = 120;
    private static final long TRANSIT_TIMEOUT = 480;
    private static final long TRANSFER_TIMEOUT = 240;
    private static final long DELIVERY_TIMEOUT = 360;

    /**
     * 检测超时未更新的包裹
     * 通过比较当前时间与最后事件时间来判断是否超时
     */
    public static Dataset<Row> detectTimeoutAnomalies(Dataset<Row> trackingEvents) {
        // 按运单号分组，获取最新状态和事件时间
        Dataset<Row> latestStatus = trackingEvents
                .groupBy("waybillNo", "orderId", "status", "stationCode", "stationName")
                .agg(
                        max("eventTime").as("lastEventTime"),
                        first("city").as("city")
                );

        // 计算滞留时间并判断是否超时
        Dataset<Row> withStagnation = latestStatus
                .withColumn("stagnation_minutes",
                        (unix_timestamp(current_timestamp()).minus(unix_timestamp(col("lastEventTime")))).divide(60))
                .withColumn("timeout_threshold",
                        when(col("status").equalTo("PICKED_UP"), lit(PICKUP_TIMEOUT))
                                .when(col("status").equalTo("IN_TRANSIT"), lit(TRANSIT_TIMEOUT))
                                .when(col("status").equalTo("AT_TRANSFER"), lit(TRANSFER_TIMEOUT))
                                .when(col("status").equalTo("OUT_FOR_DELIVERY"), lit(DELIVERY_TIMEOUT))
                                .otherwise(lit(Long.MAX_VALUE)));

        // 筛选超时记录
        return withStagnation
                .filter(col("stagnation_minutes").gt(col("timeout_threshold")))
                .withColumn("anomaly_type", lit("TIMEOUT_NO_UPDATE"))
                .withColumn("severity",
                        when(col("stagnation_minutes").gt(col("timeout_threshold").multiply(2)), lit(3))
                                .when(col("stagnation_minutes").gt(col("timeout_threshold").multiply(1.5)), lit(2))
                                .otherwise(lit(1)))
                .withColumn("detected_time", current_timestamp())
                .select("waybillNo", "orderId", "status", "stationCode", "stationName",
                        "stagnation_minutes", "anomaly_type", "severity", "detected_time");
    }

    /**
     * 检测路由回环
     * 如果包裹经过了重复的中转站，则判定为路由回环
     */
    public static Dataset<Row> detectRouteLoops(Dataset<Row> trackingEvents) {
        // 按运单号和站点分组，统计经过次数
        Dataset<Row> stationVisits = trackingEvents
                .filter(col("status").isin("IN_TRANSIT", "AT_TRANSFER"))
                .groupBy("waybillNo", "orderId", "stationCode", "stationName")
                .agg(
                        count("*").as("visit_count"),
                        max("eventTime").as("lastEventTime")
                );

        // 筛选经过次数 > 1 的记录（路由回环）
        return stationVisits
                .filter(col("visit_count").gt(1))
                .withColumn("anomaly_type", lit("ROUTE_LOOP"))
                .withColumn("severity", lit(2))
                .withColumn("stagnation_minutes", lit(0L))
                .withColumn("status", lit("AT_TRANSFER"))
                .withColumn("detected_time", current_timestamp())
                .select("waybillNo", "orderId", "status", "stationCode", "stationName",
                        "stagnation_minutes", "anomaly_type", "severity", "detected_time");
    }
}
