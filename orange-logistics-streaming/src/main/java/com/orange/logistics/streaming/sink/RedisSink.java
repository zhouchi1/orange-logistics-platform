package com.orange.logistics.streaming.sink;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;

/**
 * Redis Sink
 * 更新实时状态缓存和滑动窗口统计
 */
public class RedisSink {

    private static final String REDIS_HOST = System.getProperty("redis.host", "redis");
    private static final int REDIS_PORT = Integer.parseInt(System.getProperty("redis.port", "6379"));
    private static final JedisPool POOL;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);
        config.setMaxIdle(10);
        config.setMinIdle(5);
        POOL = new JedisPool(config, REDIS_HOST, REDIS_PORT);
    }

    /**
     * 更新异常状态到 Redis
     */
    public static void updateAnomalyStatus(Dataset<Row> batchDF, long batchId) {
        List<Row> rows = batchDF.collectAsList();

        try (Jedis jedis = POOL.getResource()) {
            var pipeline = jedis.pipelined();

            for (Row row : rows) {
                String waybillNo = row.getAs("waybillNo");
                String anomalyType = row.getAs("anomaly_type");
                int severity = row.getAs("severity");

                // 更新包裹异常状态
                String statusKey = "pkg:status:" + waybillNo;
                pipeline.hset(statusKey, "anomaly_type", anomalyType);
                pipeline.hset(statusKey, "severity", String.valueOf(severity));
                pipeline.hset(statusKey, "status", row.getAs("status"));
                pipeline.hset(statusKey, "station", row.getAs("stationCode"));
                pipeline.expire(statusKey, 86400); // 24小时过期

                // 更新异常计数（滑动窗口）
                String countKey = "anomaly:count:" + row.getAs("stationCode");
                pipeline.incr(countKey);
                pipeline.expire(countKey, 1800); // 30分钟过期

                // 发布异常告警到 Redis Pub/Sub（供 WebSocket 消费）
                String alertMessage = String.format(
                        "{\"waybillNo\":\"%s\",\"anomalyType\":\"%s\",\"severity\":%d,\"station\":\"%s\"}",
                        waybillNo, anomalyType, severity, row.getAs("stationCode"));
                pipeline.publish("logistics:anomaly:alerts", alertMessage);
            }

            pipeline.sync();
            System.out.println("[Redis Sink] Batch " + batchId + " 更新 " + rows.size() + " 条异常状态");
        } catch (Exception e) {
            System.err.println("[Redis Sink] 更新异常状态失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 更新滑动窗口统计到 Redis
     */
    public static void updateWindowStats(Dataset<Row> batchDF, long batchId) {
        List<Row> rows = batchDF.collectAsList();

        try (Jedis jedis = POOL.getResource()) {
            var pipeline = jedis.pipelined();

            for (Row row : rows) {
                String city = row.getAs("city");
                String status = row.getAs("status");
                long eventCount = row.getAs("event_count");
                long packageCount = row.getAs("package_count");

                String windowKey = "sw:stats:" + city + ":" + status;
                pipeline.hset(windowKey, "event_count", String.valueOf(eventCount));
                pipeline.hset(windowKey, "package_count", String.valueOf(packageCount));
                pipeline.hset(windowKey, "station_count", String.valueOf((long) row.getAs("station_count")));
                pipeline.expire(windowKey, 1800);
            }

            pipeline.sync();
            System.out.println("[Redis Sink] Batch " + batchId + " 更新 " + rows.size() + " 条窗口统计");
        } catch (Exception e) {
            System.err.println("[Redis Sink] 更新窗口统计失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
