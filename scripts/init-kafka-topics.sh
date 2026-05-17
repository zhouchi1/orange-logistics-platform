#!/bin/bash
# ============================================
# Kafka Topic 初始化脚→# ============================================

KAFKA_BOOTSTRAP_SERVER=${KAFKA_BOOTSTRAP_SERVER:-"localhost:29092"}

echo "=== Orange物流平台 Kafka Topic 初始→==="
echo "Kafka Server: $KAFKA_BOOTSTRAP_SERVER"
echo ""

# 等待 Kafka 就绪
echo "等待 Kafka 就绪..."
for i in $(seq 1 30); do
    if kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --list > /dev/null 2>&1; then
        echo "Kafka 已就→
        break
    fi
    echo "等待→.. ($i/30)"
    sleep 2
done

# 创建物流轨迹事件 Topic
echo ""
echo "创建 Topic: logistics-tracking-events"
kafka-topics.sh --create --if-not-exists \
    --bootstrap-server $KAFKA_BOOTSTRAP_SERVER \
    --topic logistics-tracking-events \
    --partitions 6 \
    --replication-factor 1 \
    --config retention.ms=604800000 \
    --config max.message.bytes=10485760 \
    --config cleanup.policy=delete

# 创建异常告警 Topic
echo "创建 Topic: logistics-anomaly-alerts"
kafka-topics.sh --create --if-not-exists \
    --bootstrap-server $KAFKA_BOOTSTRAP_SERVER \
    --topic logistics-anomaly-alerts \
    --partitions 3 \
    --replication-factor 1 \
    --config retention.ms=259200000 \
    --config cleanup.policy=delete

# 创建预测结果 Topic
echo "创建 Topic: logistics-prediction-results"
kafka-topics.sh --create --if-not-exists \
    --bootstrap-server $KAFKA_BOOTSTRAP_SERVER \
    --topic logistics-prediction-results \
    --partitions 3 \
    --replication-factor 1 \
    --config retention.ms=259200000 \
    --config cleanup.policy=delete

# 列出所→Topic
echo ""
echo "=== 已创建的 Topics ==="
kafka-topics.sh --list --bootstrap-server $KAFKA_BOOTSTRAP_SERVER

# 查看 Topic 详情
echo ""
echo "=== Topic 详情 ==="
kafka-topics.sh --describe --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --topic logistics-tracking-events
kafka-topics.sh --describe --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --topic logistics-anomaly-alerts
kafka-topics.sh --describe --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --topic logistics-prediction-results

echo ""
echo "=== Kafka Topic 初始化完→==="
