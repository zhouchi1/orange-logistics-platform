package com.orange.logistics.im.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Channel 连接管理 * 管理所WebSocket 连接，维护用户与 Channel 的映射关 * 支持多端登录（同一用户多个 Channel */
@Slf4j
@Component
public class ChannelManager {

    /**
     * Channel 属Key：用户会话信     */
    public static final AttributeKey<UserSession> USER_SESSION_KEY = AttributeKey.valueOf("userSession");

    /**
     * userId -> Set<Channel>（支持多端登录）
     */
    private final Map<String, Set<Channel>> userChannels = new ConcurrentHashMap<>();

    /**
     * channelId -> Channel
     */
    private final Map<String, Channel> channelMap = new ConcurrentHashMap<>();

    /**
     * 注册用户 Channel
     */
    public void addChannel(String userId, Channel channel, UserSession session) {
        channel.attr(USER_SESSION_KEY).set(session);
        channelMap.put(channel.id().asShortText(), channel);
        userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel);
        log.info("[ChannelManager] 用户上线: userId={}, channelId={}, 当前在线连接器{}",
                userId, channel.id().asShortText(), channelMap.size());
    }

    /**
     * 移除 Channel
     */
    public void removeChannel(Channel channel) {
        UserSession session = channel.attr(USER_SESSION_KEY).get();
        if (session != null) {
            String userId = session.getUserId();
            Set<Channel> channels = userChannels.get(userId);
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) {
                    userChannels.remove(userId);
                    log.info("[ChannelManager] 用户完全下线: userId={}", userId);
                }
            }
        }
        channelMap.remove(channel.id().asShortText());
        log.debug("[ChannelManager] Channel移除: channelId={}, 当前在线连接器{}",
                channel.id().asShortText(), channelMap.size());
    }

    /**
     * 获取用户的所Channel（多端）
     */
    public Set<Channel> getChannels(String userId) {
        return userChannels.getOrDefault(userId, Set.of());
    }

    /**
     * 获取用户的第一Channel
     */
    public Channel getChannel(String userId) {
        Set<Channel> channels = userChannels.get(userId);
        if (channels != null && !channels.isEmpty()) {
            return channels.iterator().next();
        }
        return null;
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(String userId) {
        Set<Channel> channels = userChannels.get(userId);
        return channels != null && !channels.isEmpty();
    }

    /**
     * 判断 Channel 是否已认     */
    public boolean isAuthenticated(Channel channel) {
        return channel.attr(USER_SESSION_KEY).get() != null;
    }

    /**
     * 获取 Channel 对应的用户会     */
    public UserSession getSession(Channel channel) {
        return channel.attr(USER_SESSION_KEY).get();
    }

    /**
     * 获取用户ID（从 Channel 属性中     */
    public String getUserId(Channel channel) {
        UserSession session = channel.attr(USER_SESSION_KEY).get();
        return session != null ? session.getUserId() : null;
    }

    /**
     * 获取所有在线用户ID
     */
    public Set<String> getOnlineUserIds() {
        return userChannels.keySet();
    }

    /**
     * 获取在线用户     */
    public int getOnlineUserCount() {
        return userChannels.size();
    }

    /**
     * 获取总连接数
     */
    public int getTotalConnectionCount() {
        return channelMap.size();
    }

    /**
     * 获取所Channel
     */
    public Collection<Channel> getAllChannels() {
        return channelMap.values();
    }

    /**
     * 批量获取用户Channel
     */
    public Map<String, Set<Channel>> getChannelsForUsers(Set<String> userIds) {
        return userIds.stream()
                .filter(userChannels::containsKey)
                .collect(Collectors.toMap(
                        userId -> userId,
                        userId -> userChannels.getOrDefault(userId, Set.of())
                ));
    }
}
