-- ============================================
-- Orange物流智能实时监控平台 - ClickHouse 初始化脚→-- ============================================

CREATE DATABASE IF NOT EXISTS logistics;

-- 物流轨迹统计表（MergeTree 引擎，按日期分区→CREATE TABLE IF NOT EXISTS logistics.tracking_statistics (
    waybill_no String,
    order_id String,
    status String,
    station_code String,
    city String,
    province String,
    event_time DateTime,
    processing_time DateTime,
    event_date Date DEFAULT toDate(event_time)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, city, station_code, waybill_no)
TTL event_date + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- 异常统计→CREATE TABLE IF NOT EXISTS logistics.anomaly_statistics (
    waybill_no String,
    order_id String,
    anomaly_type String,
    station_code String,
    city String,
    severity UInt8,
    stagnation_minutes UInt64,
    detected_time DateTime,
    detected_date Date DEFAULT toDate(detected_time)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(detected_date)
ORDER BY (detected_date, anomaly_type, city, station_code)
TTL detected_date + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

-- 每小时聚合物化视→CREATE MATERIALIZED VIEW IF NOT EXISTS logistics.hourly_tracking_stats
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, city, status)
AS SELECT
    toStartOfHour(event_time) AS hour,
    city,
    status,
    count() AS event_count,
    uniqExact(waybill_no) AS package_count,
    uniqExact(station_code) AS station_count
FROM logistics.tracking_statistics
GROUP BY hour, city, status;

-- 每日异常聚合物化视图
CREATE MATERIALIZED VIEW IF NOT EXISTS logistics.daily_anomaly_stats
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(day)
ORDER BY (day, anomaly_type, city)
AS SELECT
    toDate(detected_time) AS day,
    anomaly_type,
    city,
    count() AS anomaly_count,
    avg(severity) AS avg_severity,
    max(stagnation_minutes) AS max_stagnation
FROM logistics.anomaly_statistics
GROUP BY day, anomaly_type, city;

-- 站点吞吐量统计视→CREATE MATERIALIZED VIEW IF NOT EXISTS logistics.station_throughput
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, station_code)
AS SELECT
    toStartOfHour(event_time) AS hour,
    station_code,
    city,
    count() AS throughput,
    uniqExact(waybill_no) AS unique_packages
FROM logistics.tracking_statistics
GROUP BY hour, station_code, city;
