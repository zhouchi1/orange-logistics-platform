package com.orange.logistics.service.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.model.dto.AnomalyAlertDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 异常告警 WebSocket 处理器
 * 实时推送异常告警到前端
 */
@Component
public class AnomalyAlertWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AnomalyAlertWebSocketHandler.class);
    private final ObjectMapper objectMapper;

    // 多播 Sink，支持多个 WebSocket 客户端订阅
    private final Sinks.Many<AnomalyAlertDTO> alertSink = Sinks.many().multicast().onBackpressureBuffer(256);

    public AnomalyAlertWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket 客户端连接: {}", session.getId());

        Flux<WebSocketMessage> output = alertSink.asFlux()
                .map(alert -> {
                    try {
                        String json = objectMapper.writeValueAsString(alert);
                        return session.textMessage(json);
                    } catch (Exception e) {
                        log.error("序列化告警消息失败", e);
                        return session.textMessage("{\"error\":\"serialization failed\"}");
                    }
                });

        return session.send(output)
                .doOnTerminate(() -> log.info("WebSocket 客户端断开: {}", session.getId()));
    }

    /**
     * 发布异常告警（供 Kafka Consumer 调用）
     */
    public void publishAlert(AnomalyAlertDTO alert) {
        alertSink.tryEmitNext(alert);
    }
}
