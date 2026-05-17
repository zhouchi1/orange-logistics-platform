package com.orange.logistics.im.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageCodec;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.session.ChannelManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Set;

/**
 * 消息推送服务
 * 单聊推送、群聊推送、广播推送、跨节点推送
 */
@Slf4j
@Service
public class PushService {

    private static final String CROSS_NODE_CHANNEL = "im:cross-node:message";

    private final ChannelManager channelManager;
    private final MessageCodec messageCodec;
    private final OfflineMessageService offlineMessageService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PushService(ChannelManager channelManager,
                       MessageCodec messageCodec,
                       OfflineMessageService offlineMessageService,
                       ReactiveStringRedisTemplate redisTemplate) {
        this.channelManager = channelManager;
        this.messageCodec = messageCodec;
        this.offlineMessageService = offlineMessageService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 单聊推送
     * 直接找到目标 Channel 发送，不在线则存离线消息
     */
    public Mono<Void> pushToUser(String userId, ImMessage message) {
        Set<Channel> channels = channelManager.getChannels(userId);

        if (channels != null && !channels.isEmpty()) {
            // 用户在本节点在线，直接推送
            String json = messageCodec.encode(message);
            if (json != null) {
                TextWebSocketFrame frame = new TextWebSocketFrame(json);
                channels.forEach(channel -> {
                    if (channel.isActive()) {
                        channel.writeAndFlush(frame.retainedDuplicate());
                    }
                });
                log.debug("[Push] 单聊推送成功: toUserId={}, messageId={}", userId, message.getMessageId());
            }
            return Mono.empty();
        } else {
            // 用户不在本节点，尝试跨节点推送
            return pushCrossNode(userId, message)
                    .then(Mono.defer(() -> {
                        // 同时存储离线消息作为兜底
                        return offlineMessageService.saveOfflineMessage(userId, message);
                    }));
        }
    }

    /**
     * 群聊推送
     * 遍历群成员在线列表推送
     */
    public Mono<Void> pushToGroup(Set<String> memberIds, ImMessage message, String excludeUserId) {
        String json = messageCodec.encode(message);
        if (json == null) {
            return Mono.empty();
        }

        TextWebSocketFrame frame = new TextWebSocketFrame(json);

        return Mono.fromRunnable(() -> {
            memberIds.stream()
                    .filter(memberId -> !memberId.equals(excludeUserId))
                    .forEach(memberId -> {
                        Set<Channel> channels = channelManager.getChannels(memberId);
                        if (channels != null && !channels.isEmpty()) {
                            channels.forEach(channel -> {
                                if (channel.isActive()) {
                                    channel.writeAndFlush(frame.retainedDuplicate());
                                }
                            });
                        } else {
                            // 离线成员存储离线消息
                            offlineMessageService.saveOfflineMessage(memberId, message).subscribe();
                        }
                    });
            log.debug("[Push] 群聊推送完成: groupId={}, memberCount={}", message.getGroupId(), memberIds.size());
        });
    }

    /**
     * 广播推送（系统通知）
     */
    public void broadcast(ImMessage message) {
        String json = messageCodec.encode(message);
        if (json == null) return;

        TextWebSocketFrame frame = new TextWebSocketFrame(json);
        Collection<Channel> allChannels = channelManager.getAllChannels();

        allChannels.forEach(channel -> {
            if (channel.isActive()) {
                channel.writeAndFlush(frame.retainedDuplicate());
            }
        });

        log.info("[Push] 广播推送完成: 推送到 {} 个连接", allChannels.size());
    }

    /**
     * 跨节点推送（通过 Redis Pub/Sub）
     */
    private Mono<Void> pushCrossNode(String userId, ImMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(new CrossNodeMessage(userId, message));
            return redisTemplate.convertAndSend(CROSS_NODE_CHANNEL, payload).then();
        } catch (JsonProcessingException e) {
            log.error("[Push] 跨节点消息序列化失败: {}", e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * 跨节点消息包装
     */
    public record CrossNodeMessage(String targetUserId, ImMessage message) {}
}
