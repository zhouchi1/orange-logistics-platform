package com.orange.logistics.im.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Redis 分布式会话存 * 用于多实例部署时的会话同步和在线状态管 */
@Slf4j
@Component
public class RedisSessionStore {

    private static final String SESSION_KEY_PREFIX = "im:session:";
    private static final String ONLINE_SET_KEY = "im:online:users";
    private static final String USER_SERVER_KEY_PREFIX = "im:user:server:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${im.server-node:${spring.application.name}:${server.port:8093}}")
    private String serverNode;

    public RedisSessionStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 用户上线：存储会话信息到 Redis
     */
    public Mono<Void> userOnline(UserSession session) {
        String sessionKey = SESSION_KEY_PREFIX + session.getUserId();
        session.setServerNode(serverNode);

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(session))
                .flatMap(json -> redisTemplate.opsForValue()
                        .set(sessionKey, json, Duration.ofHours(24))
                        .then(redisTemplate.opsForSet().add(ONLINE_SET_KEY, session.getUserId()))
                        .then(redisTemplate.opsForValue()
                                .set(USER_SERVER_KEY_PREFIX + session.getUserId(), serverNode, Duration.ofHours(24)))
                        .then())
                .doOnSuccess(v -> log.info("[RedisSession] 用户上线: userId={}, server={}",
                        session.getUserId(), serverNode))
                .onErrorResume(e -> {
                    log.error("[RedisSession] 用户上线失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 用户下线：移除会话信     */
    public Mono<Void> userOffline(String userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;

        return redisTemplate.delete(sessionKey)
                .then(redisTemplate.opsForSet().remove(ONLINE_SET_KEY, userId))
                .then(redisTemplate.delete(USER_SERVER_KEY_PREFIX + userId))
                .then()
                .doOnSuccess(v -> log.info("[RedisSession] 用户下线: userId={}", userId))
                .onErrorResume(e -> {
                    log.error("[RedisSession] 用户下线失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 查询用户是否在线
     */
    public Mono<Boolean> isOnline(String userId) {
        return redisTemplate.opsForSet().isMember(ONLINE_SET_KEY, userId)
                .defaultIfEmpty(false);
    }

    /**
     * 获取用户所在服务器节点
     */
    public Mono<String> getUserServerNode(String userId) {
        return redisTemplate.opsForValue().get(USER_SERVER_KEY_PREFIX + userId);
    }

    /**
     * 获取用户会话信息
     */
    public Mono<UserSession> getSession(String userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(sessionKey)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, UserSession.class));
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                });
    }

    /**
     * 获取所有在线用户ID
     */
    public Flux<String> getOnlineUsers() {
        return redisTemplate.opsForSet().members(ONLINE_SET_KEY);
    }

    /**
     * 获取在线用户     */
    public Mono<Long> getOnlineCount() {
        return redisTemplate.opsForSet().size(ONLINE_SET_KEY);
    }

    /**
     * 更新用户最后活跃时     */
    public Mono<Void> updateLastActiveTime(String userId) {
        String key = "im:user:active:" + userId;
        return redisTemplate.opsForValue()
                .set(key, String.valueOf(System.currentTimeMillis()), Duration.ofHours(24))
                .then();
    }

    /**
     * 判断用户是否在当前节     */
    public Mono<Boolean> isOnCurrentNode(String userId) {
        return getUserServerNode(userId)
                .map(node -> serverNode.equals(node))
                .defaultIfEmpty(false);
    }
}
