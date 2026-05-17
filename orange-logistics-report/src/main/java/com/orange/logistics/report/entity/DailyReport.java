package com.orange.logistics.report.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日报表实 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("daily_report")
public class DailyReport {

    @Id
    private Long id;

    /**
     * 报表日期
     */
    private LocalDate reportDate;

    /**
     * 区域（省份）
     */
    private String province;

    /**
     * 区域（城市）
     */
    private String city;

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
     * 妥投= 已签总单     */
    private BigDecimal deliveryRate;

    /**
     * 时效达成本= 准时签收/总签     */
    private BigDecimal onTimeRate;

    /**
     * 平均配送时长（小时     */
    private BigDecimal avgDeliveryHours;

    /**
     * 异常单量
     */
    private Long exceptionOrders;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
