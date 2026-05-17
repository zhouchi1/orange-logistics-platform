package com.orange.logistics.risk.dto;

import com.orange.logistics.risk.enums.RiskLevel;
import com.orange.logistics.risk.enums.RiskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 风险评估响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentResponse {

    /**
     * 综合风险分数 (0-100)
     */
    private Integer totalScore;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 是否通过（LOW/MEDIUM通过，HIGH/CRITICAL拦截     */
    private Boolean passed;

    /**
     * 命中的风险项
     */
    private List<RiskDetail> riskDetails;

    /**
     * 建议操作
     */
    private String suggestion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDetail {
        private RiskType riskType;
        private Integer score;
        private String description;
    }
}
