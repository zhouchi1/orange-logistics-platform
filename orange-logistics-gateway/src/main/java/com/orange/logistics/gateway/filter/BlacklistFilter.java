package com.orange.logistics.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * IP 黑名单过滤器
 * Redis 存储黑名单IP，命中直接返回 403
 */
@Slf4j
@Component
public class BlacklistFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_KEY_PREFIX = "gateway:blacklist:ip:";
    private static final String BLACKLIST_SET_KEY = "gateway:blacklist:ips";

    private final ReactiveStringRedisTemplate redisTemplate;

    public BlacklistFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIp(request);

        // 检查 IP 是否在黑名单中
        return redisTemplate.opsForSet().isMember(BLACKLIST_SET_KEY, clientIp)
                .defaultIfEmpty(false)
                .flatMap(isMember -> {
                    if (Boolean.TRUE.equals(isMember)) {
                        log.warn("[Blacklist] IP被拦截: {}", clientIp);
                        return forbiddenResponse(exchange, clientIp);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    // Redis 不可用时放行，不影响正常请求
                    log.error("[Blacklist] Redis查询异常，放行请求: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
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

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String ip) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":403,\"message\":\"访问被拒绝\",\"data\":null,\"timestamp\":%d}",
                System.currentTimeMillis()
        );

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -250; // 在认证之前执行
    }
}
