package com.orange.logistics.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险评估请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 订单     */
    private String orderNo;

    /**
     * 收件人手机号
     */
    private String phone;

    /**
     * 收件地址
     */
    private String address;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 区县
     */
    private String district;
}
