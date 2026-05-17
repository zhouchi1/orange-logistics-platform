package com.orange.logistics.service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.model.dto.AnomalyAlertDTO;
import com.orange.logistics.service.websocket.AnomalyAlertWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.kafka.receiver.KafkaReceiver;

/**
 * Kafka 异常告警消费者 * 消费 Spark 产生的异常告警并推送到 WebSocket
 */
@Component
public class AnomalyAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnomalyAlertConsumer.class);

    private final KafkaReceiver<String, String> kafkaReceiver;
    private final AnomalyAlertWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public AnomalyAlertConsumer(KafkaReceiver<String, String> kafkaReceiver,
                                AnomalyAlertWebSocketHandler webSocketHandler,
                                ObjectMapper objectMapper) {
        this.kafkaReceiver = kafkaReceiver;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startConsuming() {
        kafkaReceiver.receive()
                .doOnNext(record -> {
                    try {
                        AnomalyAlertDTO alert = objectMapper.readValue(
                                record.value(), AnomalyAlertDTO.class);
                        webSocketHandler.publishAlert(alert);
                        log.debug("推送异常告 waybillNo={}, type={}",
                                alert.getWaybillNo(), alert.getAnomalyType());
                    } catch (Exception e) {
                        log.error("处理异常告警消息失败: {}", e.getMessage());
                    }
                })
                .doOnError(e -> log.error("Kafka 消费异常: {}", e.getMessage()))
                .subscribe();

        log.info("异常告警 Kafka 消费者已启动");
    }
}
