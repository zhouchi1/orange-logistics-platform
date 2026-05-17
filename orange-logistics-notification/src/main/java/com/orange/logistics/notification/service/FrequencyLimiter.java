package com.orange.logistics.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知频率限制器
 * 基于 Redis 滑动窗口算法
 *
 * 规则：
 * 1. 同一用户同一事件类型 24h 内最多发3次
 * 2. 同一用户所有事件 1h 内最多发10次
 * 3. 夜间免打扰（22:00-08:00）非紧急消息不发送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrequencyLimiter {

    private final StringRedisTemplate redisTemplate;

    // 限制规则
    private static final int MAX_SAME_EVENT_24H = 3;
    private static final int MAX_ALL_EVENTS_1H = 10;
    private static final int QUIET_HOUR_START = 22;
    private static final int QUIET_HOUR_END = 8;

    // Lua 脚本：滑动窗口计数
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            -- 移除窗口外的记录
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            
            -- 获取当前窗口内的计数
            local count = redis.call('ZCARD', key)
            
            if count < limit then
                -- 未超限，添加当前记录
                redis.call('ZADD', key, now, now .. '-' .. math.random(100000))
                redis.call('EXPIRE', key, window / 1000 + 60)
                return 1
            else
                return 0
            end
            """;

    /**
     * 检查是否允许发送
     *
     * @param userId    用户ID
     * @param eventType 事件类型
     * @param urgent    是否紧急
     * @return 检查结果
     */
    public LimitResult checkLimit(String userId, String eventType, boolean urgent) {
        // 1. 夜间免打扰检查
        if (!urgent && isQuietHours()) {
            return LimitResult.blocked("QUIET_HOURS",
                    "夜间免打扰时段（22:00-08:00），非紧急消息将延迟发送");
        }

        // 2. 同一事件 24h 限制
        String eventKey = "notify:limit:event:" + userId + ":" + eventType;
        boolean eventAllowed = slidingWindowCheck(eventKey, 24 * 3600 * 1000L, MAX_SAME_EVENT_24H);
        if (!eventAllowed) {
            return LimitResult.blocked("EVENT_LIMIT",
                    String.format("同一事件24h内已发送%d次，达到上限", MAX_SAME_EVENT_24H));
        }

        // 3. 全局 1h 限制
        String globalKey = "notify:limit:global:" + userId;
        boolean globalAllowed = slidingWindowCheck(globalKey, 3600 * 1000L, MAX_ALL_EVENTS_1H);
        if (!globalAllowed) {
            return LimitResult.blocked("GLOBAL_LIMIT",
                    String.format("1h内已发送%d条通知，达到上限", MAX_ALL_EVENTS_1H));
        }

        return LimitResult.pass();
    }

    /**
     * 获取用户当前频率状态
     */
    public Map<String, Object> getStatus(String userId, String eventType) {
        long now = System.currentTimeMillis();

        // 24h 内同一事件发送次数
        String eventKey = "notify:limit:event:" + userId + ":" + eventType;
        Long eventCount = redisTemplate.opsForZSet().count(eventKey,
                now - 24 * 3600 * 1000L, now);

        // 1h 内全局发送次数
        String globalKey = "notify:limit:global:" + userId;
        Long globalCount = redisTemplate.opsForZSet().count(globalKey,
                now - 3600 * 1000L, now);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("userId", userId);
        status.put("eventType", eventType);
        status.put("eventCount24h", eventCount != null ? eventCount : 0);
        status.put("eventLimit24h", MAX_SAME_EVENT_24H);
        status.put("globalCount1h", globalCount != null ? globalCount : 0);
        status.put("globalLimit1h", MAX_ALL_EVENTS_1H);
        status.put("isQuietHours", isQuietHours());
        status.put("canSend", (eventCount == null || eventCount < MAX_SAME_EVENT_24H)
                && (globalCount == null || globalCount < MAX_ALL_EVENTS_1H));

        return status;
    }

    /**
     * 重置用户频率计数（管理员操作）
     */
    public void resetLimit(String userId, String eventType) {
        String eventKey = "notify:limit:event:" + userId + ":" + eventType;
        String globalKey = "notify:limit:global:" + userId;
        redisTemplate.delete(eventKey);
        redisTemplate.delete(globalKey);
        log.info("重置用户频率限制: userId={} eventType={}", userId, eventType);
    }

    // ========== 私有方法 ==========

    private boolean slidingWindowCheck(String key, long windowMs, int limit) {
        long now = System.currentTimeMillis();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(windowMs),
                String.valueOf(limit),
                String.valueOf(now));
        return result != null && result == 1;
    }

    private boolean isQuietHours() {
        int hour = LocalTime.now().getHour();
        return hour >= QUIET_HOUR_START || hour < QUIET_HOUR_END;
    }

    // ========== 结果类 ==========

    public record LimitResult(boolean allowed, String code, String message) {
        public static LimitResult pass() {
            return new LimitResult(true, "OK", "允许发送");
        }

        public static LimitResult blocked(String code, String message) {
            return new LimitResult(false, code, message);
        }
    }
}
