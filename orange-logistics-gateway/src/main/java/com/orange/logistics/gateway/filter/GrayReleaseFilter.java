package com.orange.logistics.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度发布过滤器
 * 根据请求头 X-Gray-Version 路由到灰度实例
 * 支持按用户ID百分比灰度
 */
@Slf4j
@Component
public class GrayReleaseFilter implements GlobalFilter, Ordered {

    private static final String GRAY_VERSION_HEADER = "X-Gray-Version";
    private static final String GRAY_METADATA_KEY = "gray";

    /**
     * 灰度流量百分比（0-100），可通过 Nacos 配置动态调整
     */
    private volatile int grayPercent = 10;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 显式指定灰度版本
        String grayVersion = request.getHeaders().getFirst(GRAY_VERSION_HEADER);
        if (grayVersion != null && !grayVersion.isEmpty()) {
            log.debug("[Gray] 显式灰度路由: version={}", grayVersion);
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Service-Version", grayVersion)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // 2. 按用户ID百分比灰度
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            // 使用用户ID的哈希值确保同一用户始终路由到同一版本
            int hash = Math.abs(userId.hashCode() % 100);
            if (hash < grayPercent) {
                log.debug("[Gray] 用户灰度命中: userId={}, hash={}", userId, hash);
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-Service-Version", "gray")
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }
        } else {
            // 未认证用户随机灰度
            if (ThreadLocalRandom.current().nextInt(100) < grayPercent) {
                log.debug("[Gray] 随机灰度命中");
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-Service-Version", "gray")
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }
        }

        // 3. 正常流量
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Service-Version", "stable")
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 动态更新灰度百分比（由 Nacos 配置监听器调用）
     */
    public void updateGrayPercent(int percent) {
        if (percent >= 0 && percent <= 100) {
            this.grayPercent = percent;
            log.info("[Gray] 灰度百分比更新: {}%", percent);
        }
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
