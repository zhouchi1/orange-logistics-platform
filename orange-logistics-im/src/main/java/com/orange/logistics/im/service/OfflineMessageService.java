package com.orange.logistics.im.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageType;
import lombok.extern.slf4j.Slf4j;
// import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 离线消息服务
 * 用户不在线时消息写入 RocketMQ / Redis
 * 用户上线后拉取离线消 */
@Slf4j
@Service
public class OfflineMessageService {

    private static final String OFFLINE_MSG_KEY_PREFIX = "im:offline:";
    private static final int MAX_OFFLINE_MESSAGES = 200;
    private static final Duration OFFLINE_EXPIRE = Duration.ofDays(7);

    private final ReactiveStringRedisTemplate redisTemplate;
    // private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfflineMessageService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 存储离线消息
     */
    public Mono<Void> saveOfflineMessage(String userId, ImMessage message) {
        String key = OFFLINE_MSG_KEY_PREFIX + userId;

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
                .flatMap(json -> {
                    // 使用 Redis List 存储离线消息
                    return redisTemplate.opsForList().rightPush(key, json)
                            .flatMap(size -> {
                                // 限制离线消息数量
                                if (size > MAX_OFFLINE_MESSAGES) {
                                    return redisTemplate.opsForList().leftPop(key).then();
                                }
                                return Mono.empty();
                            })
                            .then(redisTemplate.expire(key, OFFLINE_EXPIRE))
                            .then();
                })
                .doOnSuccess(v -> log.debug("[OfflineMsg] 离线消息已存 userId={}, messageId={}",
                        userId, message.getMessageId()))
                .onErrorResume(e -> {
                    log.error("[OfflineMsg] 存储离线消息失败: {}", e.getMessage());
                    // 降级：RocketMQ disabled for local dev
                    log.warn("[OfflineMsg] RocketMQ降级已禁用，消息丢弃: userId={}", userId);
                    return Mono.empty();
                });
    }

    /**
     * 拉取离线消息
     */
    public Flux<ImMessage> pullOfflineMessages(String userId) {
        String key = OFFLINE_MSG_KEY_PREFIX + userId;

        return redisTemplate.opsForList().size(key)
                .flatMapMany(size -> {
                    if (size == null || size == 0) {
                        return Flux.empty();
                    }
                    int pullCount = (int) Math.min(size, MAX_OFFLINE_MESSAGES);
                    return redisTemplate.opsForList().range(key, 0, pullCount - 1);
                })
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, ImMessage.class));
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                })
                .doOnComplete(() -> {
                    // 拉取后清除离线消                    redisTemplate.delete(key).subscribe();
                    log.info("[OfflineMsg] 离线消息已拉取并清除: userId={}", userId);
                });
    }

    /**
     * 获取离线消息数量
     */
    public Mono<Long> getOfflineMessageCount(String userId) {
        String key = OFFLINE_MSG_KEY_PREFIX + userId;
        return redisTemplate.opsForList().size(key).defaultIfEmpty(0L);
    }

    /**
     * 清除用户离线消息
     */
    public Mono<Void> clearOfflineMessages(String userId) {
        String key = OFFLINE_MSG_KEY_PREFIX + userId;
        return redisTemplate.delete(key).then();
    }

    /**
     * 降级：发送到 RocketMQ (disabled for local dev)
     */
    private void sendToRocketMQ(String userId, ImMessage message) {
        log.info("[OfflineMsg] RocketMQ已禁用，跳过发送: userId={}", userId);
    }
}
