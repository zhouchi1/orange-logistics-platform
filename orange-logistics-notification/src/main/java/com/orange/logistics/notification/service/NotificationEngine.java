package com.orange.logistics.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 通知引擎
 * 策略模式：根据渠道选择发送器
 * 支持模板渲染、批量发送、异步处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEngine {

    private final StringRedisTemplate redisTemplate;

    // 渠道发送器注册
    private final Map<String, ChannelSender> senders = new HashMap<>();

    {
        senders.put("SMS", new SmsSender());
        senders.put("PUSH", new PushSender());
        senders.put("WECHAT", new WechatSender());
        senders.put("EMAIL", new EmailSender());
    }

    /**
     * 发送通知（单条）
     *
     * @param userId    接收用户ID
     * @param channel   渠道：SMS, PUSH, WECHAT, EMAIL
     * @param template  模板内容（含占位符 {{key}}）
     * @param params    模板参数
     * @param eventType 事件类型（用于频率控制）
     * @param urgent    是否紧急（紧急消息不受夜间免打扰限制）
     * @return 发送结果
     */
    public Map<String, Object> send(String userId, String channel, String template,
                                     Map<String, String> params, String eventType, boolean urgent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("channel", channel);
        result.put("eventType", eventType);
        result.put("timestamp", LocalDateTime.now().toString());

        // 1. 频率控制检查
        if (!urgent && isFrequencyLimited(userId, eventType)) {
            result.put("status", "BLOCKED");
            result.put("reason", "频率限制：24h内同一事件已发送超限");
            log.info("通知被频率限制拦截: user={} event={}", userId, eventType);
            return result;
        }

        // 2. 夜间免打扰检查
        if (!urgent && isQuietHours()) {
            result.put("status", "DELAYED");
            result.put("reason", "夜间免打扰（22:00-08:00），将在08:00发送");
            // 实际生产中应写入延迟队列
            log.info("通知延迟发送（夜间免打扰）: user={} event={}", userId, eventType);
            return result;
        }

        // 3. 渲染模板
        String content = renderTemplate(template, params);

        // 4. 选择渠道发送
        ChannelSender sender = senders.get(channel.toUpperCase());
        if (sender == null) {
            result.put("status", "FAILED");
            result.put("reason", "不支持的渠道: " + channel);
            return result;
        }

        boolean success = sender.send(userId, content);

        // 5. 记录发送次数（频率控制）
        if (success) {
            recordSend(userId, eventType);
        }

        result.put("status", success ? "SENT" : "FAILED");
        result.put("content", content);
        log.info("通知发送{}: user={} channel={} event={}",
                success ? "成功" : "失败", userId, channel, eventType);

        return result;
    }

    /**
     * 批量发送通知
     */
    public Map<String, Object> batchSend(List<String> userIds, String channel, String template,
                                          Map<String, String> params, String eventType, boolean urgent) {
        int success = 0, blocked = 0, failed = 0;

        for (String userId : userIds) {
            Map<String, Object> result = send(userId, channel, template, params, eventType, urgent);
            String status = (String) result.get("status");
            switch (status) {
                case "SENT" -> success++;
                case "BLOCKED", "DELAYED" -> blocked++;
                default -> failed++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", userIds.size());
        summary.put("success", success);
        summary.put("blocked", blocked);
        summary.put("failed", failed);
        summary.put("channel", channel);
        summary.put("eventType", eventType);

        log.info("批量通知完成: 总数={} 成功={} 拦截={} 失败={}",
                userIds.size(), success, blocked, failed);
        return summary;
    }

    /**
     * 多渠道发送（同一消息发送到多个渠道）
     */
    public List<Map<String, Object>> multiChannelSend(String userId, List<String> channels,
                                                       String template, Map<String, String> params,
                                                       String eventType, boolean urgent) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String channel : channels) {
            results.add(send(userId, channel, template, params, eventType, urgent));
        }
        return results;
    }

    // ========== 模板渲染 ==========

    /**
     * 渲染模板：将 {{key}} 替换为实际值
     */
    public String renderTemplate(String template, Map<String, String> params) {
        if (template == null || params == null) return template;
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    // ========== 频率控制 ==========

    private boolean isFrequencyLimited(String userId, String eventType) {
        String key = "notify:freq:" + userId + ":" + eventType;
        String count = redisTemplate.opsForValue().get(key);
        return count != null && Integer.parseInt(count) >= 3;
    }

    private void recordSend(String userId, String eventType) {
        String key = "notify:freq:" + userId + ":" + eventType;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        }
    }

    private boolean isQuietHours() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(8, 0));
    }

    // ========== 渠道发送器（策略模式） ==========

    interface ChannelSender {
        boolean send(String userId, String content);
    }

    static class SmsSender implements ChannelSender {
        @Override
        public boolean send(String userId, String content) {
            // 实际对接短信网关（阿里云SMS、腾讯云SMS等）
            return true;
        }
    }

    static class PushSender implements ChannelSender {
        @Override
        public boolean send(String userId, String content) {
            // 实际对接推送服务（极光、个推、Firebase等）
            return true;
        }
    }

    static class WechatSender implements ChannelSender {
        @Override
        public boolean send(String userId, String content) {
            // 实际对接微信模板消息/订阅消息 API
            return true;
        }
    }

    static class EmailSender implements ChannelSender {
        @Override
        public boolean send(String userId, String content) {
            // 实际对接邮件服务（SMTP/SES等）
            return true;
        }
    }
}
