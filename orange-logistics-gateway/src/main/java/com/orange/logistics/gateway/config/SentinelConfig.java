package com.orange.logistics.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Sentinel 熔断降级配置
 * 自定义降级返回
 */
@Slf4j
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void init() {
        // 自定义限流降级返回
        GatewayCallbackManager.setBlockHandler(new CustomBlockRequestHandler());
        log.info("[Sentinel] 自定义限流降级处理器初始化完成");
    }

    /**
     * 自定义限流降级返回处理器
     */
    static class CustomBlockRequestHandler implements BlockRequestHandler {

        @Override
        public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 429);
            result.put("message", "系统繁忙，请稍后再试");
            result.put("data", null);
            result.put("timestamp", System.currentTimeMillis());
            result.put("traceId", exchange.getRequest().getHeaders().getFirst("X-Trace-Id"));

            log.warn("[Sentinel] 请求被限流降级: {} {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getPath());

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(result);
        }
    }
}
