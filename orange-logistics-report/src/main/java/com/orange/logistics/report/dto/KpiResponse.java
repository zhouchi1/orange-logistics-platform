package com.orange.logistics.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * KPI 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiResponse {

    /**
     * 妥投= 已签总单     */
    private BigDecimal deliveryRate;

    /**
     * 时效达成本= 准时签收/总签     */
    private BigDecimal onTimeRate;

    /**
     * 总单     */
    private Long totalOrders;

    /**
     * 已签收单     */
    private Long signedOrders;

    /**
     * 准时签收单量
     */
    private Long onTimeOrders;

    /**
     * 异常单量
     */
    private Long exceptionOrders;

    /**
     * 平均配送时长（小时     */
    private BigDecimal avgDeliveryHours;

    /**
     * 统计周期描述
     */
    private String period;
}
