package com.orange.logistics.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一错误响应格式
 */
@Slf4j
@Order(-1)
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 如果响应已经提交，直接返回
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus httpStatus;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            httpStatus = HttpStatus.valueOf(rse.getStatusCode().value());
            message = switch (httpStatus) {
                case NOT_FOUND -> "服务未找到";
                case BAD_GATEWAY -> "网关错误，上游服务不可用";
                case SERVICE_UNAVAILABLE -> "服务暂时不可用";
                case GATEWAY_TIMEOUT -> "网关超时，上游服务响应过慢";
                default -> rse.getReason() != null ? rse.getReason() : httpStatus.getReasonPhrase();
            };
        } else if (ex instanceof java.net.ConnectException) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            message = "服务连接失败，请稍后重试";
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            httpStatus = HttpStatus.GATEWAY_TIMEOUT;
            message = "请求超时，请稍后重试";
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "系统内部错误";
        }

        log.error("[GatewayException] {} {} -> {} : {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                httpStatus.value(),
                ex.getMessage());

        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> result = new HashMap<>();
        result.put("code", httpStatus.value());
        result.put("message", message);
        result.put("data", null);
        result.put("traceId", exchange.getRequest().getHeaders().getFirst("X-Trace-Id"));
        result.put("timestamp", System.currentTimeMillis());

        String body;
        try {
            body = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            body = String.format("{\"code\":%d,\"message\":\"%s\",\"timestamp\":%d}",
                    httpStatus.value(), message, System.currentTimeMillis());
        }

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
