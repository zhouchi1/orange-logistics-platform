package com.orange.logistics.model.dto;

import com.orange.logistics.model.enums.AnomalyType;
import com.orange.logistics.model.enums.LogisticsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异常告警 DTO（WebSocket 推+ Kafka 消息体）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyAlertDTO {

    private String waybillNo;
    private String orderId;
    private AnomalyType anomalyType;
    private LogisticsStatus currentStatus;
    private String stationCode;
    private String stationName;
    private String description;
    private Long stagnationMinutes;
    private Integer severity;
    private LocalDateTime detectedTime;
}
