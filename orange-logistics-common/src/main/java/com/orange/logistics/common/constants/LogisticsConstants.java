package com.orange.logistics.common.constants;

/**
 * 物流状态常 */
public final class LogisticsConstants {

    private LogisticsConstants() {}

    // Kafka Topics
    public static final String TOPIC_TRACKING_EVENTS = "logistics-tracking-events";
    public static final String TOPIC_ANOMALY_ALERTS = "logistics-anomaly-alerts";
    public static final String TOPIC_PREDICTION_RESULTS = "logistics-prediction-results";

    // Redis Keys
    public static final String REDIS_KEY_PACKAGE_STATUS = "pkg:status:";
    public static final String REDIS_KEY_PACKAGE_LOCATION = "pkg:location:";
    public static final String REDIS_KEY_ANOMALY_COUNT = "anomaly:count:";
    public static final String REDIS_KEY_SLIDING_WINDOW = "sw:anomaly:";

    // 超时阈值（分钟    public static final long PICKUP_TIMEOUT_MINUTES = 120;        // 揽收超时
    public static final long TRANSIT_TIMEOUT_MINUTES = 480;       // 运输超时
    public static final long TRANSFER_TIMEOUT_MINUTES = 240;      // 中转超时
    public static final long DELIVERY_TIMEOUT_MINUTES = 360;      // 派送超
    // 滑动窗口配置
    public static final int SLIDING_WINDOW_SIZE_MINUTES = 30;
    public static final int ANOMALY_THRESHOLD_COUNT = 5;

    // Elasticsearch Index
    public static final String ES_INDEX_TRACKING = "logistics-tracking";
    public static final String ES_INDEX_ANOMALY = "logistics-anomaly";

    // ClickHouse Tables
    public static final String CH_TABLE_TRACKING_STATS = "tracking_statistics";
    public static final String CH_TABLE_ANOMALY_STATS = "anomaly_statistics";
}
