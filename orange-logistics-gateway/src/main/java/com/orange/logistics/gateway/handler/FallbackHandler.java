package com.orange.logistics.gateway.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级处理器
 * 服务不可用时返回友好提示
 */
@Slf4j
@Component
public class FallbackHandler implements HandlerFunction<ServerResponse> {

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        String path = request.path();
        String serviceName = extractServiceName(path);

        log.warn("[Fallback] 服务降级触发: {} -> service={}", path, serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 503);
        result.put("message", String.format("服务[%s]暂时不可用，请稍后重试", serviceName));
        result.put("data", null);
        result.put("timestamp", System.currentTimeMillis());
        result.put("traceId", request.headers().firstHeader("X-Trace-Id"));

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }

    /**
     * 从路径中提取服务名
     */
    private String extractServiceName(String path) {
        // /api/order/xxx -> order
        // /api/auth/xxx -> auth
        if (path.startsWith("/api/")) {
            String[] parts = path.substring(5).split("/");
            if (parts.length > 0) {
                return parts[0];
            }
        }
        return "unknown";
    }
}
