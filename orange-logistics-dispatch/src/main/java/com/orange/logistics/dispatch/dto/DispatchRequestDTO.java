package com.orange.logistics.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DispatchRequestDTO {
    @NotBlank(message = "运单号不能为空")
    private String waybillNo;
    private String orderNo;
    @NotBlank(message = "收件人姓名不能为空")
    private String receiverName;
    @NotBlank(message = "收件人电话不能为空")
    private String receiverPhone;
    @NotBlank(message = "收件人地址不能为空")
    private String receiverAddress;
    private BigDecimal receiverLongitude;
    private BigDecimal receiverLatitude;
    private Integer priority; // 1-普通 2-加急 3-当日达
    private Integer deliveryMethod; // 1-上门 2-驿站 3-自提柜
    private String deliveryPoint;
}
