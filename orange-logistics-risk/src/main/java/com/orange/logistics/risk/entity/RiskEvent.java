package com.orange.logistics.risk.entity;

import com.orange.logistics.risk.enums.RiskLevel;
import com.orange.logistics.risk.enums.RiskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 风险事件记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("risk_event")
public class RiskEvent {

    @Id
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 订单     */
    private String orderNo;

    /**
     * 风险类型
     */
    private RiskType riskType;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 风险分数 (0-100)
     */
    private Integer riskScore;

    /**
     * 风险描述
     */
    private String description;

    /**
     * 处理状态：PENDING / APPROVED / REJECTED
     */
    private String handleStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
