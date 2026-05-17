-- ============================================
-- 橙子便利物流智能实时监控平台 - MySQL 初始化脚本
-- ============================================

-- Nacos 配置数据库
CREATE DATABASE IF NOT EXISTS nacos_config
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 业务数据库
CREATE DATABASE IF NOT EXISTS orange_logistics
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE orange_logistics;

-- 物流轨迹事件表
CREATE TABLE IF NOT EXISTS tracking_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waybill_no VARCHAR(32) NOT NULL COMMENT '运单号',
    order_id VARCHAR(32) NOT NULL COMMENT '订单号',
    status VARCHAR(32) NOT NULL COMMENT '物流状态',
    station_code VARCHAR(32) NOT NULL COMMENT '站点编码',
    station_name VARCHAR(128) COMMENT '站点名称',
    city VARCHAR(32) COMMENT '城市',
    province VARCHAR(32) COMMENT '省份',
    latitude DOUBLE COMMENT '纬度',
    longitude DOUBLE COMMENT '经度',
    operator VARCHAR(64) COMMENT '操作人',
    remark VARCHAR(512) COMMENT '备注',
    event_time DATETIME NOT NULL COMMENT '事件时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_waybill_no (waybill_no),
    INDEX idx_order_id (order_id),
    INDEX idx_station_code (station_code),
    INDEX idx_city (city),
    INDEX idx_event_time (event_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='物流轨迹事件表';

-- 异常记录表
CREATE TABLE IF NOT EXISTS anomaly_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waybill_no VARCHAR(32) NOT NULL COMMENT '运单号',
    order_id VARCHAR(32) NOT NULL COMMENT '订单号',
    anomaly_type VARCHAR(32) NOT NULL COMMENT '异常类型',
    current_status VARCHAR(32) COMMENT '当前状态',
    station_code VARCHAR(32) COMMENT '站点编码',
    station_name VARCHAR(128) COMMENT '站点名称',
    description VARCHAR(512) COMMENT '异常描述',
    stagnation_minutes BIGINT DEFAULT 0 COMMENT '滞留时间(分钟)',
    severity INT DEFAULT 1 COMMENT '严重程度(1-3)',
    resolved BOOLEAN DEFAULT FALSE COMMENT '是否已解决',
    detected_time DATETIME NOT NULL COMMENT '检测时间',
    resolved_time DATETIME COMMENT '解决时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_waybill_no (waybill_no),
    INDEX idx_anomaly_type (anomaly_type),
    INDEX idx_station_code (station_code),
    INDEX idx_severity (severity),
    INDEX idx_resolved (resolved),
    INDEX idx_detected_time (detected_time),
    UNIQUE KEY uk_waybill_anomaly (waybill_no, anomaly_type, station_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='异常记录表';

-- 站点信息表
CREATE TABLE IF NOT EXISTS station_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_code VARCHAR(32) NOT NULL UNIQUE COMMENT '站点编码',
    station_name VARCHAR(128) NOT NULL COMMENT '站点名称',
    station_type VARCHAR(32) NOT NULL COMMENT '站点类型',
    city VARCHAR(32) COMMENT '城市',
    province VARCHAR(32) COMMENT '省份',
    latitude DOUBLE COMMENT '纬度',
    longitude DOUBLE COMMENT '经度',
    capacity INT DEFAULT 0 COMMENT '处理能力(件/小时)',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '站点状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_city (city),
    INDEX idx_station_type (station_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='站点信息表';

-- 预测结果表
CREATE TABLE IF NOT EXISTS prediction_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waybill_no VARCHAR(32) NOT NULL COMMENT '运单号',
    order_id VARCHAR(32) NOT NULL COMMENT '订单号',
    origin_city VARCHAR(32) COMMENT '出发城市',
    destination_city VARCHAR(32) COMMENT '目的城市',
    predicted_hours DOUBLE COMMENT '预测时效(小时)',
    actual_hours DOUBLE COMMENT '实际时效(小时)',
    confidence DOUBLE COMMENT '置信度',
    model_version VARCHAR(32) COMMENT '模型版本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_waybill_no (waybill_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='预测结果表';

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waybill_no VARCHAR(32) UNIQUE COMMENT '运单号',
    sender_name VARCHAR(64) COMMENT '寄件人',
    sender_phone VARCHAR(20) COMMENT '寄件人电话',
    sender_province VARCHAR(32) COMMENT '寄件省份',
    sender_city VARCHAR(32) COMMENT '寄件城市',
    sender_address VARCHAR(256) COMMENT '寄件地址',
    receiver_name VARCHAR(64) COMMENT '收件人',
    receiver_phone VARCHAR(20) COMMENT '收件人电话',
    receiver_province VARCHAR(32) COMMENT '收件省份',
    receiver_city VARCHAR(32) COMMENT '收件城市',
    receiver_address VARCHAR(256) COMMENT '收件地址',
    status VARCHAR(32) DEFAULT 'CREATED' COMMENT '订单状态',
    close_reason VARCHAR(128) COMMENT '关闭原因',
    weight DECIMAL(10,2) COMMENT '重量(kg)',
    volume DECIMAL(10,2) COMMENT '体积(m3)',
    freight DECIMAL(10,2) COMMENT '运费',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_waybill_no (waybill_no),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_sender_province (sender_province),
    INDEX idx_receiver_city (receiver_city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='订单表';

-- 日报表
CREATE TABLE IF NOT EXISTS daily_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL COMMENT '报表日期',
    province VARCHAR(32) COMMENT '省份',
    city VARCHAR(32) COMMENT '城市',
    total_orders BIGINT DEFAULT 0 COMMENT '总单量',
    signed_orders BIGINT DEFAULT 0 COMMENT '签收量',
    on_time_orders BIGINT DEFAULT 0 COMMENT '准时签收量',
    delivery_rate DECIMAL(5,2) COMMENT '妥投率(%)',
    on_time_rate DECIMAL(5,2) COMMENT '时效达成率(%)',
    avg_delivery_hours DECIMAL(8,2) COMMENT '平均配送时长(小时)',
    exception_orders BIGINT DEFAULT 0 COMMENT '异常单量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report_date (report_date),
    INDEX idx_province (province)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='日报表';

-- 任务日志表
CREATE TABLE IF NOT EXISTS task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name VARCHAR(64) NOT NULL COMMENT '任务名称',
    status VARCHAR(16) DEFAULT 'RUNNING' COMMENT '状态',
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME,
    processed_count INT DEFAULT 0 COMMENT '处理数量',
    result_message VARCHAR(512) COMMENT '结果信息',
    error_message TEXT COMMENT '错误信息',
    INDEX idx_job_name (job_name),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='定时任务日志表';

-- 通知记录表
CREATE TABLE IF NOT EXISTS notification_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) COMMENT '用户ID',
    channel VARCHAR(16) COMMENT '通知渠道',
    title VARCHAR(128) COMMENT '标题',
    content TEXT COMMENT '内容',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='通知记录表';

-- 插入示例站点数据
INSERT INTO station_info (station_code, station_name, station_type, city, province, latitude, longitude, capacity) VALUES
('BJ-R1', '北京揽收站', '揽收站', '北京', '北京', 39.9042, 116.4074, 5000),
('BJ-F1', '北京分拣中心', '分拣中心', '北京', '北京', 39.9142, 116.4174, 20000),
('BJ-Z1', '北京中转站', '中转站', '北京', '北京', 39.9242, 116.4274, 15000),
('BJ-P1', '北京配送站', '配送站', '北京', '北京', 39.9342, 116.4374, 3000),
('SH-R1', '上海揽收站', '揽收站', '上海', '上海', 31.2304, 121.4737, 6000),
('SH-F1', '上海分拣中心', '分拣中心', '上海', '上海', 31.2404, 121.4837, 25000),
('SH-Z1', '上海中转站', '中转站', '上海', '上海', 31.2504, 121.4937, 18000),
('SH-P1', '上海配送站', '配送站', '上海', '上海', 31.2604, 121.5037, 4000),
('GZ-R1', '广州揽收站', '揽收站', '广州', '广东', 23.1291, 113.2644, 5500),
('GZ-F1', '广州分拣中心', '分拣中心', '广州', '广东', 23.1391, 113.2744, 22000),
('SZ-R1', '深圳揽收站', '揽收站', '深圳', '广东', 22.5431, 114.0579, 5000),
('HZ-R1', '杭州揽收站', '揽收站', '杭州', '浙江', 30.2741, 120.1551, 4500),
('CD-R1', '成都揽收站', '揽收站', '成都', '四川', 30.5728, 104.0668, 4000),
('WH-R1', '武汉揽收站', '揽收站', '武汉', '湖北', 30.5928, 114.3055, 4500),
('NJ-R1', '南京揽收站', '揽收站', '南京', '江苏', 32.0603, 118.7969, 4000);
