package com.orange.logistics.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 区域统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionStatistics {

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 总单     */
    private Long totalOrders;

    /**
     * 已签收单     */
    private Long signedOrders;

    /**
     * 妥投     */
    private BigDecimal deliveryRate;

    /**
     * 时效达成本     */
    private BigDecimal onTimeRate;

    /**
     * 平均配送时     */
    private BigDecimal avgDeliveryHours;
}
