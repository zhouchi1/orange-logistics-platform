package com.orange.logistics.model.dto;

import com.orange.logistics.model.enums.LogisticsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物流轨迹事件 DTO（Kafka 消息体）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEventDTO {

    private String waybillNo;
    private String orderId;
    private LogisticsStatus status;
    private String stationCode;
    private String stationName;
    private String city;
    private String province;
    private Double latitude;
    private Double longitude;
    private String operator;
    private String remark;
    private LocalDateTime eventTime;
}
