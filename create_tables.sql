-- =============================================
-- orange_logistics_warehouse tables
-- =============================================
USE orange_logistics_warehouse;

CREATE TABLE IF NOT EXISTS warehouse (
    id BIGINT PRIMARY KEY,
    warehouse_code VARCHAR(50) NOT NULL,
    warehouse_name VARCHAR(100) NOT NULL,
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    address VARCHAR(255),
    longitude DOUBLE,
    latitude DOUBLE,
    total_area INT DEFAULT 0,
    used_area INT DEFAULT 0,
    type INT DEFAULT 1 COMMENT '1-普通仓 2-冷链仓 3-保税仓',
    status INT DEFAULT 1 COMMENT '0-停用 1-启用',
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    deleted INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_warehouse_code (warehouse_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warehouse_location (
    id BIGINT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    zone VARCHAR(20),
    aisle VARCHAR(20),
    shelf VARCHAR(20),
    layer INT,
    type INT DEFAULT 1 COMMENT '1-存储位 2-拣货位 3-暂存位',
    status INT DEFAULT 0 COMMENT '0-空闲 1-占用 2-锁定',
    max_weight INT,
    max_volume INT,
    deleted INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_location_code (location_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    location_id BIGINT,
    sku_code VARCHAR(50) NOT NULL,
    sku_name VARCHAR(200),
    quantity INT DEFAULT 0,
    locked_quantity INT DEFAULT 0,
    available_quantity INT DEFAULT 0,
    batch_no VARCHAR(50),
    production_date DATETIME,
    expiration_date DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_warehouse_sku (warehouse_id, sku_code),
    INDEX idx_sku_code (sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inbound_order (
    id BIGINT PRIMARY KEY,
    inbound_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    type INT DEFAULT 1 COMMENT '1-采购入库 2-退货入库 3-调拨入库',
    status INT DEFAULT 0 COMMENT '0-待收货 1-收货中 2-已完成',
    supplier_name VARCHAR(100),
    total_quantity INT DEFAULT 0,
    received_quantity INT DEFAULT 0,
    operator VARCHAR(50),
    remark VARCHAR(500),
    deleted INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_inbound_no (inbound_no),
    INDEX idx_warehouse_id (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS outbound_order (
    id BIGINT PRIMARY KEY,
    outbound_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    order_id BIGINT,
    order_no VARCHAR(50),
    type INT DEFAULT 1 COMMENT '1-销售出库 2-调拨出库 3-报废出库',
    status INT DEFAULT 0 COMMENT '0-待拣货 1-拣货中 2-已拣货 3-已出库',
    total_quantity INT DEFAULT 0,
    picked_quantity INT DEFAULT 0,
    wave_id BIGINT,
    operator VARCHAR(50),
    remark VARCHAR(500),
    deleted INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_outbound_no (outbound_no),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pick_wave (
    id BIGINT PRIMARY KEY,
    wave_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status INT DEFAULT 0 COMMENT '0-待执行 1-执行中 2-已完成',
    order_count INT DEFAULT 0,
    total_items INT DEFAULT 0,
    operator VARCHAR(50),
    deleted INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wave_no (wave_no),
    INDEX idx_warehouse_id (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- orange_logistics tables (notification, risk, im, etc.)
-- =============================================
USE orange_logistics;

CREATE TABLE IF NOT EXISTS notification_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(50) NOT NULL,
    template_name VARCHAR(100),
    channel_type VARCHAR(20) COMMENT 'SMS/EMAIL/PUSH/WECHAT',
    content TEXT,
    title VARCHAR(200),
    enabled TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS risk_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50),
    order_no VARCHAR(50),
    risk_type VARCHAR(30) COMMENT 'ADDRESS_FRAUD/WEIGHT_ANOMALY/FREQUENCY_ABUSE/PAYMENT_RISK',
    risk_level VARCHAR(20) COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    risk_score INT DEFAULT 0,
    description VARCHAR(500),
    handle_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no),
    INDEX idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL COMMENT 'USER/PHONE/ADDRESS',
    value VARCHAR(200) NOT NULL,
    reason VARCHAR(500),
    enabled TINYINT(1) DEFAULT 1,
    expire_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    INDEX idx_type_value (type, value),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS im_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    message_type VARCHAR(20) COMMENT 'CHAT/GROUP_CHAT',
    from_user_id VARCHAR(50),
    from_user_name VARCHAR(100),
    to_id VARCHAR(50),
    group_id VARCHAR(50),
    content TEXT,
    content_type VARCHAR(30) DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/VOICE/FILE/LOCATION/LOGISTICS_CARD',
    extra TEXT,
    status VARCHAR(20) DEFAULT 'SENT' COMMENT 'SENT/DELIVERED/READ',
    recalled TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_message_id (message_id),
    INDEX idx_from_user (from_user_id),
    INDEX idx_to_id (to_id),
    INDEX idx_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS im_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL,
    conversation_type VARCHAR(20) COMMENT 'PRIVATE/GROUP',
    user_id VARCHAR(50) NOT NULL,
    target_id VARCHAR(50),
    last_message TEXT,
    last_message_time DATETIME,
    unread_count INT DEFAULT 0,
    pinned TINYINT(1) DEFAULT 0,
    muted TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS im_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL,
    group_name VARCHAR(100),
    avatar VARCHAR(500),
    owner_id VARCHAR(50),
    announcement TEXT,
    group_type VARCHAR(20) COMMENT 'TEAM/CUSTOMER/DISPATCH',
    member_count INT DEFAULT 0,
    max_members INT DEFAULT 500,
    mute_all TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_group_id (group_id),
    INDEX idx_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS im_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    username VARCHAR(100),
    nickname VARCHAR(100),
    role VARCHAR(20) DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER',
    muted TINYINT(1) DEFAULT 0,
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_group_id (group_id),
    INDEX idx_user_id (user_id),
    UNIQUE INDEX idx_group_user (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
