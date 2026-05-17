package com.orange.logistics.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 多维度限流过滤器
 * 支持 IP 限流、用户限流、接口限流
 * 基于 Redis + Lua 脚本的滑动窗口算法
 */
@Slf4j
@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 滑动窗口限流 Lua 脚本
     * KEYS[1] = 限流key
     * ARGV[1] = 窗口大小（秒）
     * ARGV[2] = 最大请求数
     * ARGV[3] = 当前时间戳（毫秒）
     */
    private static final String RATE_LIMIT_LUA_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1]) * 1000
            local max_requests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local window_start = now - window
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
            local current = redis.call('ZCARD', key)
            if current >= max_requests then
                return 0
            end
            redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
            redis.call('EXPIRE', key, tonumber(ARGV[1]) + 1)
            return 1
            """;

    private final RedisScript<Long> rateLimitScript;

    // 默认限流配置
    private static final int IP_RATE_LIMIT = 200;        // IP: 200次/分钟
    private static final int USER_RATE_LIMIT = 100;      // 用户: 100次/分钟
    private static final int API_RATE_LIMIT = 500;       // 接口: 500次/分钟
    private static final int WINDOW_SECONDS = 60;        // 窗口: 60秒

    public RequestRateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = RedisScript.of(RATE_LIMIT_LUA_SCRIPT, Long.class);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String ip = getClientIp(request);
        String path = request.getURI().getPath();
        String userId = request.getHeaders().getFirst("X-User-Id");

        String now = String.valueOf(System.currentTimeMillis());

        // IP 限流
        Mono<Boolean> ipCheck = checkRateLimit("rate_limit:ip:" + ip, WINDOW_SECONDS, IP_RATE_LIMIT, now);

        // 用户限流（已认证用户）
        Mono<Boolean> userCheck;
        if (userId != null && !userId.isEmpty()) {
            userCheck = checkRateLimit("rate_limit:user:" + userId, WINDOW_SECONDS, USER_RATE_LIMIT, now);
        } else {
            userCheck = Mono.just(true);
        }

        // 接口限流
        Mono<Boolean> apiCheck = checkRateLimit("rate_limit:api:" + path, WINDOW_SECONDS, API_RATE_LIMIT, now);

        return Mono.zip(ipCheck, userCheck, apiCheck)
                .flatMap(tuple -> {
                    boolean ipAllowed = tuple.getT1();
                    boolean userAllowed = tuple.getT2();
                    boolean apiAllowed = tuple.getT3();

                    if (!ipAllowed) {
                        log.warn("[RateLimit] IP限流触发: {} -> {}", ip, path);
                        return tooManyRequestsResponse(exchange, "请求过于频繁（IP限流），请稍后再试");
                    }
                    if (!userAllowed) {
                        log.warn("[RateLimit] 用户限流触发: userId={} -> {}", userId, path);
                        return tooManyRequestsResponse(exchange, "请求过于频繁（用户限流），请稍后再试");
                    }
                    if (!apiAllowed) {
                        log.warn("[RateLimit] 接口限流触发: {}", path);
                        return tooManyRequestsResponse(exchange, "接口访问过于频繁，请稍后再试");
                    }

                    return chain.filter(exchange);
                });
    }

    private Mono<Boolean> checkRateLimit(String key, int windowSeconds, int maxRequests, String now) {
        List<String> keys = List.of(key);
        return redisTemplate.execute(rateLimitScript, keys,
                        Arrays.asList(String.valueOf(windowSeconds), String.valueOf(maxRequests), now))
                .next()
                .map(result -> result != null && result == 1L)
                .defaultIfEmpty(true); // Redis 不可用时放行
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> tooManyRequestsResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":429,\"message\":\"%s\",\"data\":null,\"timestamp\":%d}",
                message, System.currentTimeMillis()
        );

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -150;
    }
}
