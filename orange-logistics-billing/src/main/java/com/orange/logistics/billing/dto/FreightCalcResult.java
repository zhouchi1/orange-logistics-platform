package com.orange.logistics.billing.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FreightCalcResult {
    private BigDecimal baseFreight;       // 基础运费(首重)
    private BigDecimal weightFreight;     // 续重运费
    private BigDecimal distanceSurcharge; // 距离附加费
    private BigDecimal serviceMarkup;     // 时效加价
    private BigDecimal couponDiscount;    // 优惠券抵扣
    private BigDecimal totalFreight;      // 总运费(优惠前)
    private BigDecimal actualAmount;      // 实付金额(优惠后)
    private String ruleApplied;           // 应用的规则名称
}
