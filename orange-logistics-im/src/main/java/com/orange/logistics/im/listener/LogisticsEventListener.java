package com.orange.logistics.im.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 物流事件监听器
 * 监听 Kafka 物流轨迹事件，实时推送给相关用户（寄件人、收件人）
 * 消息格式：物流卡片（运单号、当前状态、位置、时间）
 */
@Slf4j
@Component
public class LogisticsEventListener {

    private final PushService pushService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogisticsEventListener(PushService pushService, ReactiveStringRedisTemplate redisTemplate) {
        this.pushService = pushService;
        this.redisTemplate = redisTemplate;
    }

    // 状态中文映射
    private static final Map<String, String> STATUS_MAP = Map.of(
            "PICKED_UP", "已揽收",
            "IN_TRANSIT", "运输中",
            "AT_TRANSFER", "到达中转站",
            "OUT_FOR_DELIVERY", "正在派送",
            "DELIVERED", "已签收",
            "EXCEPTION", "异常"
    );

    // 需要推送的关键状态变更
    private static final Set<String> PUSH_STATUSES = Set.of(
            "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "EXCEPTION"
    );

    /**
     * 监听物流轨迹事件
     */
    @KafkaListener(topics = "logistics-tracking-events", groupId = "im-logistics-listener")
    public void onLogisticsEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String waybillNo = event.path("waybillNo").asText();
            String status = event.path("status").asText();
            String stationName = event.path("stationName").asText();
            String city = event.path("city").asText();
            String eventTime = event.path("eventTime").asText();

            // 只推送关键状态变更
            if (!PUSH_STATUSES.contains(status)) {
                return;
            }

            // 查找关联用户（寄件人、收件人）
            String key = "waybill:users:" + waybillNo;
            redisTemplate.opsForSet().members(key)
                    .collectList()
                    .subscribe(relatedUsers -> {
                        if (relatedUsers.isEmpty()) {
                            return;
                        }

                        // 构建物流卡片消息
                        String cardContent = buildLogisticsCard(waybillNo, status, stationName, city, eventTime);

                        // 推送给相关用户
                        for (String userId : relatedUsers) {
                            ImMessage imMessage = new ImMessage();
                            imMessage.setMessageId(UUID.randomUUID().toString());
                            imMessage.setType(MessageType.NOTIFY);
                            imMessage.setFromUserId("SYSTEM_LOGISTICS");
                            imMessage.setToId(userId);
                            imMessage.setContent(cardContent);
                            imMessage.setTimestamp(System.currentTimeMillis());
                            imMessage.setExtra(Map.of(
                                    "notifyType", "LOGISTICS",
                                    "waybillNo", waybillNo,
                                    "status", status
                            ));

                            pushService.pushToUser(userId, imMessage).subscribe();
                        }

                        log.info("物流事件推送: waybill={} status={} 推送用户数={}",
                                waybillNo, status, relatedUsers.size());
                    });

        } catch (Exception e) {
            log.error("处理物流事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 监听异常告警事件
     */
    @KafkaListener(topics = "logistics-anomaly-alerts", groupId = "im-anomaly-listener")
    public void onAnomalyAlert(String message) {
        try {
            JsonNode alert = objectMapper.readTree(message);

            String waybillNo = alert.path("waybillNo").asText();
            String anomalyType = alert.path("anomalyType").asText();
            String description = alert.path("description").asText();

            // 推送给运营人员
            redisTemplate.opsForSet().members("im:operators")
                    .collectList()
                    .subscribe(operators -> {
                        List<String> operatorList = operators.isEmpty()
                                ? List.of("admin", "operator1", "operator2")
                                : operators;

                        String alertContent = String.format(
                                "\u26A0\uFE0F 物流异常告警\n运单号: %s\n异常类型: %s\n描述: %s\n时间: %s",
                                waybillNo, anomalyType, description,
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        );

                        for (String userId : operatorList) {
                            ImMessage imMessage = new ImMessage();
                            imMessage.setMessageId(UUID.randomUUID().toString());
                            imMessage.setType(MessageType.NOTIFY);
                            imMessage.setFromUserId("SYSTEM_ALERT");
                            imMessage.setToId(userId);
                            imMessage.setContent(alertContent);
                            imMessage.setTimestamp(System.currentTimeMillis());
                            imMessage.setExtra(Map.of(
                                    "notifyType", "ANOMALY_ALERT",
                                    "waybillNo", waybillNo,
                                    "anomalyType", anomalyType,
                                    "priority", "HIGH"
                            ));

                            pushService.pushToUser(userId, imMessage).subscribe();
                        }

                        log.warn("异常告警推送: waybill={} type={} 推送运营人员数={}",
                                waybillNo, anomalyType, operatorList.size());
                    });

        } catch (Exception e) {
            log.error("处理异常告警失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建物流卡片消息
     */
    private String buildLogisticsCard(String waybillNo, String status,
                                       String stationName, String city, String eventTime) {
        String statusText = STATUS_MAP.getOrDefault(status, status);
        String emoji = getStatusEmoji(status);

        return String.format(
                "%s 物流更新\n" +
                "━━━━━━━━━━━━━━\n" +
                "\uD83D\uDCE6 运单号: %s\n" +
                "\uD83D\uDCCD 状态: %s\n" +
                "\uD83C\uDFE2 位置: %s %s\n" +
                "\uD83D\uDD50 时间: %s\n" +
                "━━━━━━━━━━━━━━",
                emoji, waybillNo, statusText, city, stationName, eventTime
        );
    }

    private String getStatusEmoji(String status) {
        return switch (status) {
            case "PICKED_UP" -> "\uD83D\uDCEC";
            case "OUT_FOR_DELIVERY" -> "\uD83D\uDE9A";
            case "DELIVERED" -> "\u2705";
            case "EXCEPTION" -> "\u26A0\uFE0F";
            default -> "\uD83D\uDCE6";
        };
    }
}
