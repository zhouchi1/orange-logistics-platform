package com.orange.logistics.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class FreightCalcDTO {
    @NotNull(message = "重量不能为空")
    private BigDecimal weight;
    private BigDecimal volume;
    private String senderCity;
    private String receiverCity;
    private BigDecimal distance; // 距离(km)
    private Integer serviceType; // 1-标准 2-加3-当日
    private Long couponId; // 优惠券ID
}
