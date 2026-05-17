package com.orange.logistics.waybill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class GenerateWaybillDTO {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    private String orderNo;
    @NotBlank(message = "寄件人姓名不能为空")
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    @NotBlank(message = "收件人姓名不能为空")
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal weight;
    private BigDecimal volume;
    private Integer itemCount;
    private String itemDescription;
}
