package com.orange.logistics.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 趋势分析数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendData {

    /**
     * 趋势类型：DAILY / WEEKLY / MONTHLY
     */
    private String trendType;

    /**
     * 趋势数据     */
    private List<TrendPoint> points;

    /**
     * 环比增长     */
    private BigDecimal chainRate;

    /**
     * 同比增长     */
    private BigDecimal yearOnYearRate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private LocalDate date;
        private Long totalOrders;
        private BigDecimal deliveryRate;
        private BigDecimal onTimeRate;
    }
}
