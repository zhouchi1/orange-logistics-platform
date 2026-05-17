package com.orange.logistics.notification.service;

import com.orange.logistics.notification.dto.NotificationResponse;
import com.orange.logistics.notification.dto.SendNotificationRequest;
import com.orange.logistics.notification.entity.NotificationRecord;
import com.orange.logistics.notification.entity.NotificationTemplate;
import com.orange.logistics.notification.enums.ChannelType;
import com.orange.logistics.notification.enums.NotificationStatus;
import com.orange.logistics.notification.repository.RecordRepository;
import com.orange.logistics.notification.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 通知服务核心逻辑
 * - 多渠道发送（策略模式）
 * - 模板渲染（占位符替换）
 * - 频率控制：Redis 记录发送次数，同一用户同一事件24h内不重复
 * - 异步发送（Reactor）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TemplateRepository templateRepository;
    private final RecordRepository recordRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final SmsService smsService;
    private final PushService pushService;
    private final EmailService emailService;
    private final WechatService wechatService;

    /**
     * 发送通知（支持多渠道）
     */
    public Mono<NotificationResponse> sendNotification(SendNotificationRequest request) {
        String requestId = UUID.randomUUID().toString();

        // 1. 频率控制检查
        return checkThrottle(request.getUserId(), request.getEventType())
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.info("频率限制：用户={}, 事件={}", request.getUserId(), request.getEventType());
                        return Mono.just(NotificationResponse.builder()
                                .requestId(requestId)
                                .success(false)
                                .results(java.util.List.of(NotificationResponse.ChannelResult.builder()
                                        .status(NotificationStatus.THROTTLED)
                                        .message("24小时内已发送过相同通知")
                                        .build()))
                                .build());
                    }

                    // 2. 获取模板并渲染
                    return templateRepository.findByTemplateCodeAndEnabledTrue(request.getTemplateCode())
                            .switchIfEmpty(Mono.error(new RuntimeException("模板不存在或已禁用: " + request.getTemplateCode())))
                            .flatMap(template -> {
                                String renderedContent = renderTemplate(template.getContent(), request.getVariables());
                                String title = template.getTitle() != null
                                        ? renderTemplate(template.getTitle(), request.getVariables())
                                        : "";

                                // 3. 多渠道异步发送
                                return Flux.fromIterable(request.getChannels())
                                        .flatMap(channel -> sendByChannel(channel, request, renderedContent, title))
                                        .collectList()
                                        .flatMap(results -> {
                                            // 4. 记录发送频率
                                            return markThrottle(request.getUserId(), request.getEventType())
                                                    .thenReturn(NotificationResponse.builder()
                                                            .requestId(requestId)
                                                            .success(results.stream()
                                                                    .allMatch(r -> r.getStatus() == NotificationStatus.SUCCESS))
                                                            .results(results)
                                                            .build());
                                        });
                            });
                });
    }

    /**
     * 模板渲染：替换 ${variable} 占位符
     */
    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null || variables == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 策略模式：根据渠道类型选择发送方式
     */
    private Mono<NotificationResponse.ChannelResult> sendByChannel(
            ChannelType channel, SendNotificationRequest request, String content, String title) {

        Mono<Boolean> sendResult = switch (channel) {
            case SMS -> smsService.send(request.getReceiver(), content);
            case PUSH -> pushService.send(request.getUserId(), title, content);
            case EMAIL -> emailService.send(request.getReceiver(), title, content);
            case WECHAT -> wechatService.send(request.getReceiver(), content);
        };

        return sendResult.flatMap(success -> {
            // 保存发送记录
            NotificationRecord record = NotificationRecord.builder()
                    .userId(request.getUserId())
                    .eventType(request.getEventType())
                    .channelType(channel)
                    .receiver(request.getReceiver())
                    .content(content)
                    .status(success ? NotificationStatus.SUCCESS : NotificationStatus.FAILED)
                    .failReason(success ? null : "发送失败")
                    .sentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            return recordRepository.save(record)
                    .thenReturn(NotificationResponse.ChannelResult.builder()
                            .channel(channel)
                            .status(success ? NotificationStatus.SUCCESS : NotificationStatus.FAILED)
                            .message(success ? "发送成功" : "发送失败")
                            .sentAt(LocalDateTime.now())
                            .build());
        });
    }

    /**
     * 频率控制检查：同一用户同一事件24h内不重复发送
     */
    private Mono<Boolean> checkThrottle(String userId, String eventType) {
        String key = buildThrottleKey(userId, eventType);
        return redisTemplate.hasKey(key).map(exists -> !exists);
    }

    /**
     * 标记已发送（设置24h过期）
     */
    private Mono<Boolean> markThrottle(String userId, String eventType) {
        String key = buildThrottleKey(userId, eventType);
        return redisTemplate.opsForValue()
                .set(key, "1", Duration.ofHours(24));
    }

    private String buildThrottleKey(String userId, String eventType) {
        return "notification:throttle:" + userId + ":" + eventType;
    }

    /**
     * 查询用户通知记录
     */
    public Flux<NotificationRecord> getUserRecords(String userId) {
        return recordRepository.findByUserId(userId);
    }
}
