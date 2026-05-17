package com.orange.logistics.im.service;

import com.orange.logistics.im.session.ChannelManager;
import com.orange.logistics.im.session.RedisSessionStore;
import com.orange.logistics.im.session.UserSession;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 会话管理服务
 * 管理用户在线状态、连接管理
 */
@Slf4j
@Service
public class SessionService {

    private final ChannelManager channelManager;
    private final RedisSessionStore redisSessionStore;

    public SessionService(ChannelManager channelManager, RedisSessionStore redisSessionStore) {
        this.channelManager = channelManager;
        this.redisSessionStore = redisSessionStore;
    }

    /**
     * 用户上线
     */
    public Mono<Void> userOnline(String userId, String username, String roles, Channel channel, String deviceType) {
        UserSession session = UserSession.builder()
                .userId(userId)
                .username(username)
                .roles(roles)
                .channelId(channel.id().asShortText())
                .deviceType(deviceType != null ? deviceType : "WEB")
                .connectTime(System.currentTimeMillis())
                .lastActiveTime(System.currentTimeMillis())
                .build();

        // 本地注册
        channelManager.addChannel(userId, channel, session);

        // Redis 注册（分布式）
        return redisSessionStore.userOnline(session);
    }

    /**
     * 用户下线
     */
    public Mono<Void> userOffline(Channel channel) {
        String userId = channelManager.getUserId(channel);
        if (userId == null) {
            return Mono.empty();
        }

        // 本地移除
        channelManager.removeChannel(channel);

        // 如果用户没有其他连接了，Redis 移除
        if (!channelManager.isOnline(userId)) {
            return redisSessionStore.userOffline(userId);
        }
        return Mono.empty();
    }

    /**
     * 判断用户是否在线（本地 + Redis）
     */
    public Mono<Boolean> isUserOnline(String userId) {
        // 先查本地
        if (channelManager.isOnline(userId)) {
            return Mono.just(true);
        }
        // 再查 Redis（可能在其他节点）
        return redisSessionStore.isOnline(userId);
    }

    /**
     * 获取用户的所有 Channel
     */
    public Set<Channel> getUserChannels(String userId) {
        return channelManager.getChannels(userId);
    }

    /**
     * 获取在线用户数
     */
    public Mono<Long> getOnlineCount() {
        return redisSessionStore.getOnlineCount();
    }

    /**
     * 更新用户活跃时间
     */
    public Mono<Void> updateActiveTime(String userId) {
        return redisSessionStore.updateLastActiveTime(userId);
    }

    /**
     * 获取用户所在服务器节点
     */
    public Mono<String> getUserServerNode(String userId) {
        return redisSessionStore.getUserServerNode(userId);
    }
}
