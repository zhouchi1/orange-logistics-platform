package com.orange.logistics.streaming.sink;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * MySQL Sink
 * 将异常记录写入 MySQL
 */
public class MySQLSink {

    private static final String MYSQL_URL = System.getProperty("mysql.url",
            "jdbc:mysql://mysql:3306/orange_logistics?useSSL=false&serverTimezone=Asia/Shanghai");
    private static final String MYSQL_USER = System.getProperty("mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "orange_logistics_2024");

    /**
     * 批量写入异常记录到 MySQL
     */
    public static void writeAnomalies(Dataset<Row> batchDF, long batchId) {
        String insertSQL = """
                INSERT INTO anomaly_record 
                (waybill_no, order_id, anomaly_type, current_status, station_code, station_name,
                 stagnation_minutes, severity, detected_time, resolved, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, NOW())
                ON DUPLICATE KEY UPDATE 
                stagnation_minutes = VALUES(stagnation_minutes),
                severity = VALUES(severity),
                detected_time = VALUES(detected_time)
                """;

        List<Row> rows = batchDF.collectAsList();

        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(insertSQL)) {

            for (Row row : rows) {
                ps.setString(1, row.getAs("waybillNo"));
                ps.setString(2, row.getAs("orderId"));
                ps.setString(3, row.getAs("anomaly_type"));
                ps.setString(4, row.getAs("status"));
                ps.setString(5, row.getAs("stationCode"));
                ps.setString(6, row.getAs("stationName"));
                ps.setLong(7, ((Number) row.getAs("stagnation_minutes")).longValue());
                ps.setInt(8, row.getAs("severity"));
                ps.setTimestamp(9, java.sql.Timestamp.valueOf(row.getAs("detected_time").toString()));
                ps.addBatch();
            }

            ps.executeBatch();
            System.out.println("[MySQL Sink] Batch " + batchId + " 写入 " + rows.size() + " 条异常记录");

        } catch (Exception e) {
            System.err.println("[MySQL Sink] 写入失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
