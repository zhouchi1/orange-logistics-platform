package com.orange.logistics.streaming.sink;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * ClickHouse Sink
 * 将统计数据写入 ClickHouse 用于 OLAP 分析
 */
public class ClickHouseSink {

    private static final String CH_URL = System.getProperty("clickhouse.url",
            "jdbc:clickhouse://clickhouse:8123/logistics");
    private static final String CH_USER = System.getProperty("clickhouse.user", "default");
    private static final String CH_PASSWORD = System.getProperty("clickhouse.password", "");

    /**
     * 批量写入轨迹统计数据到 ClickHouse
     */
    public static void writeBatch(Dataset<Row> batchDF, long batchId) {
        String insertSQL = """
                INSERT INTO tracking_statistics 
                (waybill_no, order_id, status, station_code, city, province, event_time, processing_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        List<Row> rows = batchDF.collectAsList();

        try (Connection conn = DriverManager.getConnection(CH_URL, CH_USER, CH_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(insertSQL)) {

            for (Row row : rows) {
                ps.setString(1, row.getAs("waybillNo"));
                ps.setString(2, row.getAs("orderId"));
                ps.setString(3, row.getAs("status"));
                ps.setString(4, row.getAs("stationCode"));
                ps.setString(5, row.getAs("city"));
                ps.setString(6, row.getAs("province"));
                ps.setTimestamp(7, java.sql.Timestamp.valueOf(row.getAs("eventTime").toString()));
                ps.setTimestamp(8, java.sql.Timestamp.valueOf(row.getAs("processing_time").toString()));
                ps.addBatch();
            }

            ps.executeBatch();
            System.out.println("[ClickHouse Sink] Batch " + batchId + " 写入 " + rows.size() + " 条记录");

        } catch (Exception e) {
            System.err.println("[ClickHouse Sink] 写入失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
