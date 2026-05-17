-- Nacos Config MySQL Schema
-- Source: https://github.com/alibaba/nacos/blob/master/distribution/conf/mysql-schema.sql

CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nacos_config;

CREATE TABLE IF NOT EXISTS config_info (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) DEFAULT NULL,
    content longtext NOT NULL,
    md5 varchar(32) DEFAULT NULL,
    gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    src_user text,
    src_ip varchar(50) DEFAULT NULL,
    app_name varchar(128) DEFAULT NULL,
    tenant_id varchar(128) DEFAULT '',
    c_desc varchar(256) DEFAULT NULL,
    c_use varchar(64) DEFAULT NULL,
    effect varchar(64) DEFAULT NULL,
    type varchar(64) DEFAULT NULL,
    c_schema text,
    encrypted_data_key varchar(1024) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfo_datagrouptenant (data_id,group_id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_info_aggr (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    datum_id varchar(255) NOT NULL,
    content longtext NOT NULL,
    gmt_modified datetime NOT NULL,
    app_name varchar(128) DEFAULT NULL,
    tenant_id varchar(128) DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfoaggr_datagrouptenantdatum (data_id,group_id,tenant_id,datum_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_info_beta (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    app_name varchar(128) DEFAULT NULL,
    content longtext NOT NULL,
    beta_ips varchar(1024) DEFAULT NULL,
    md5 varchar(32) DEFAULT NULL,
    gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    src_user text,
    src_ip varchar(50) DEFAULT NULL,
    tenant_id varchar(128) DEFAULT '',
    encrypted_data_key varchar(1024) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfobeta_datagrouptenant (data_id,group_id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_info_tag (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    tenant_id varchar(128) DEFAULT '',
    tag_id varchar(128) NOT NULL,
    app_name varchar(128) DEFAULT NULL,
    content longtext NOT NULL,
    md5 varchar(32) DEFAULT NULL,
    gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    src_user text,
    src_ip varchar(50) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfotag_datagrouptenanttag (data_id,group_id,tenant_id,tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS config_tags_relation (
    id bigint(20) NOT NULL,
    tag_name varchar(128) NOT NULL,
    tag_type varchar(64) DEFAULT NULL,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    tenant_id varchar(128) DEFAULT '',
    nid bigint(20) NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (nid),
    UNIQUE KEY uk_configtagrelation_configidtag (id,tag_name,tag_type),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS group_capacity (
    id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    group_id varchar(128) NOT NULL DEFAULT '',
    quota int(10) unsigned NOT NULL DEFAULT '0',
    `usage` int(10) unsigned NOT NULL DEFAULT '0',
    max_size int(10) unsigned NOT NULL DEFAULT '0',
    max_aggr_count int(10) unsigned NOT NULL DEFAULT '0',
    max_aggr_size int(10) unsigned NOT NULL DEFAULT '0',
    max_history_count int(10) unsigned NOT NULL DEFAULT '0',
    gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS his_config_info (
    id bigint(20) unsigned NOT NULL,
    nid bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    app_name varchar(128) DEFAULT NULL,
    content longtext NOT NULL,
    md5 varchar(32) DEFAULT NULL,
    gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    src_user text,
    src_ip varchar(50) DEFAULT NULL,
    op_type char(10) DEFAULT NULL,
    tenant_id varchar(128) DEFAULT '',
    encrypted_data_key varchar(1024) NOT NULL DEFAULT '',
    publish_type varchar(50) DEFAULT 'formal',
    ext_info longtext DEFAULT NULL,
    PRIMARY KEY (nid),
    KEY idx_gmt_create (gmt_create),
    KEY idx_gmt_modified (gmt_modified),
    KEY idx_did (data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenant_capacity (
    id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    tenant_id varchar(128) NOT NULL DEFAULT '',
    quota int(10) unsigned NOT NULL DEFAULT '0',
    `usage` int(10) unsigned NOT NULL DEFAULT '0',
    max_size int(10) unsigned NOT NULL DEFAULT '0',
    max_aggr_count int(10) unsigned NOT NULL DEFAULT '0',
    max_aggr_size int(10) unsigned NOT NULL DEFAULT '0',
    max_history_count int(10) unsigned NOT NULL DEFAULT '0',
    gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenant_info (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    kp varchar(128) NOT NULL,
    tenant_id varchar(128) DEFAULT '',
    tenant_name varchar(128) DEFAULT '',
    tenant_desc varchar(256) DEFAULT NULL,
    create_source varchar(32) DEFAULT NULL,
    gmt_create bigint(20) NOT NULL,
    gmt_modified bigint(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_info_kptenantid (kp,tenant_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    username varchar(50) NOT NULL PRIMARY KEY,
    password varchar(500) NOT NULL,
    enabled boolean NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS roles (
    username varchar(50) NOT NULL,
    role varchar(50) NOT NULL,
    UNIQUE KEY idx_user_role (username ASC, role ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS permissions (
    role varchar(50) NOT NULL,
    resource varchar(255) NOT NULL,
    action varchar(8) NOT NULL,
    UNIQUE KEY uk_role_permission (role,resource,action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Default user: nacos/nacos
INSERT IGNORE INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);
INSERT IGNORE INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');

-- ============================================
-- 导入初始配置到 Nacos
-- ============================================

-- common.yml 通用配置
INSERT IGNORE INTO config_info (data_id, group_id, content, md5, type, tenant_id) VALUES
('common.yml', 'DEFAULT_GROUP',
'# 橙子便利物流 - 通用配置
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
    default-property-inclusion: non_null
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB

# 日志配置
logging:
  level:
    root: INFO
    com.orange.logistics: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-}] %logger{36} - %msg%n"

# 通用业务配置
orange:
  logistics:
    waybill-prefix: OG
    default-timeout-hours: 72
    max-retry-count: 3
    cold-chain:
      temp-min: -25.0
      temp-max: 8.0
      alert-threshold: 2.0
',
MD5('common'), 'yaml', '');

-- datasource.yml 数据源配置
INSERT IGNORE INTO config_info (data_id, group_id, content, md5, type, tenant_id) VALUES
('datasource.yml', 'DEFAULT_GROUP',
'# 橙子便利物流 - 数据源配置
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/orange_logistics?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: orange_logistics_2024
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
      validation-query: SELECT 1

  # Kafka
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: orange-logistics-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3

  # Elasticsearch
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
    socket-timeout: 30s
',
MD5('datasource'), 'yaml', '');

-- redis.yml Redis配置
INSERT IGNORE INTO config_info (data_id, group_id, content, md5, type, tenant_id) VALUES
('redis.yml', 'DEFAULT_GROUP',
'# 橙子便利物流 - Redis 配置
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 3000ms
      database: 0

# Redis 缓存策略
cache:
  default-ttl: 3600
  waybill-ttl: 1800
  user-session-ttl: 7200
  rate-limit-window: 60
',
MD5('redis'), 'yaml', '');

-- gateway-routes.json 动态路由配置
INSERT IGNORE INTO config_info (data_id, group_id, content, md5, type, tenant_id) VALUES
('gateway-routes.json', 'DEFAULT_GROUP',
'[
  {
    "id": "auth-service",
    "uri": "lb://orange-logistics-auth",
    "predicates": [{"name": "Path", "args": {"pattern": "/api/auth/**"}}],
    "filters": [{"name": "StripPrefix", "args": {"parts": "1"}}],
    "order": 1
  },
  {
    "id": "order-service",
    "uri": "lb://orange-logistics-order",
    "predicates": [{"name": "Path", "args": {"pattern": "/api/order/**"}}],
    "filters": [{"name": "StripPrefix", "args": {"parts": "1"}}],
    "order": 2
  },
  {
    "id": "waybill-service",
    "uri": "lb://orange-logistics-waybill",
    "predicates": [{"name": "Path", "args": {"pattern": "/api/waybill/**"}}],
    "filters": [{"name": "StripPrefix", "args": {"parts": "1"}}],
    "order": 3
  },
  {
    "id": "transport-service",
    "uri": "lb://orange-logistics-transport",
    "predicates": [{"name": "Path", "args": {"pattern": "/api/transport/**"}}],
    "filters": [{"name": "StripPrefix", "args": {"parts": "1"}}],
    "order": 4
  },
  {
    "id": "dispatch-service",
    "uri": "lb://orange-logistics-dispatch",
    "predicates": [{"name": "Path", "args": {"pattern": "/api/dispatch/**"}}],
    "filters": [{"name": "StripPrefix", "args": {"parts": "1"}}],
    "order": 5
  }
]',
MD5('routes'), 'json', '');
