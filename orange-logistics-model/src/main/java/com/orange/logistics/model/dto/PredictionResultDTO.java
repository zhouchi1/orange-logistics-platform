package com.orange.logistics.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物流时效预测结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResultDTO {

    private String waybillNo;
    private String orderId;
    private String originCity;
    private String destinationCity;
    private Double predictedHours;
    private Double confidence;
    private String modelVersion;
}
