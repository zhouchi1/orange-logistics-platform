package com.orange.logistics.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应包装过滤器
 * 统一 JSON 响应格式 {code, message, data, traceId, timestamp}
 */
@Slf4j
@Component
public class ResponseWrapFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // 只处理 JSON 响应
                MediaType contentType = getHeaders().getContentType();
                if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                    return super.writeWith(body);
                }

                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        // 合并所有 DataBuffer
                        DataBuffer joinedBuffer = bufferFactory.join(dataBuffers);
                        byte[] content = new byte[joinedBuffer.readableByteCount()];
                        joinedBuffer.read(content);
                        DataBufferUtils.release(joinedBuffer);

                        String originalBody = new String(content, StandardCharsets.UTF_8);
                        String wrappedBody = wrapResponse(exchange, originalBody);

                        byte[] wrappedBytes = wrappedBody.getBytes(StandardCharsets.UTF_8);
                        getHeaders().setContentLength(wrappedBytes.length);
                        return bufferFactory.wrap(wrappedBytes);
                    }));
                } else if (body instanceof Mono) {
                    return super.writeWith(
                            Mono.from(body).map(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);

                                String originalBody = new String(content, StandardCharsets.UTF_8);
                                String wrappedBody = wrapResponse(exchange, originalBody);

                                byte[] wrappedBytes = wrappedBody.getBytes(StandardCharsets.UTF_8);
                                getHeaders().setContentLength(wrappedBytes.length);
                                return bufferFactory.wrap(wrappedBytes);
                            })
                    );
                }

                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private String wrapResponse(ServerWebExchange exchange, String originalBody) {
        try {
            // 检查是否已经是标准格式
            Object parsed = objectMapper.readValue(originalBody, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                if (map.containsKey("code") && map.containsKey("message")) {
                    // 已经是标准格式，补充 traceId 和 timestamp
                    Map<String, Object> result = new HashMap<>((Map<String, Object>) map);
                    result.putIfAbsent("traceId", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
                    result.putIfAbsent("timestamp", System.currentTimeMillis());
                    return objectMapper.writeValueAsString(result);
                }
            }

            // 包装为标准格式
            Map<String, Object> wrapper = new HashMap<>();
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 200;
            wrapper.put("code", statusCode >= 200 && statusCode < 300 ? 200 : statusCode);
            wrapper.put("message", statusCode >= 200 && statusCode < 300 ? "success" : "error");
            wrapper.put("data", parsed);
            wrapper.put("traceId", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
            wrapper.put("timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            // 非 JSON 响应，不包装
            return originalBody;
        }
    }

    @Override
    public int getOrder() {
        return -10; // 较低优先级，在其他过滤器之后执行
    }
}
