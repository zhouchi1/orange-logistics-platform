package com.orange.logistics.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * 请求日志过滤器
 * 记录请求方法、路径、耗时、响应码
 * 生成 traceId 贯穿全链路
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String START_TIME_ATTR = "gateway_start_time";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 生成 traceId（如果上游没有传递）
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }

        // 记录开始时间
        long startTime = Instant.now().toEpochMilli();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");

        log.info("[Request] traceId={} | {} {} | IP={} | UA={}",
                traceId, method, path, clientIp,
                userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 100)) : "N/A");

        // 将 traceId 传递给下游
        final String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        // 在响应头中也添加 traceId
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    Long start = exchange.getAttribute(START_TIME_ATTR);
                    if (start != null) {
                        long duration = Instant.now().toEpochMilli() - start;
                        int statusCode = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value()
                                : 0;

                        if (duration > 3000) {
                            log.warn("[Response] traceId={} | {} {} | status={} | duration={}ms [SLOW]",
                                    finalTraceId, method, path, statusCode, duration);
                        } else {
                            log.info("[Response] traceId={} | {} {} | status={} | duration={}ms",
                                    finalTraceId, method, path, statusCode, duration);
                        }
                    }
                }));
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
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

    @Override
    public int getOrder() {
        return -300; // 最高优先级，最先执行
    }
}
