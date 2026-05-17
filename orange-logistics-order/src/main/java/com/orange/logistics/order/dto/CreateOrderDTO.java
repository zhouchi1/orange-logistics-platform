package com.orange.logistics.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateOrderDTO {
    private Long customerId;

    @NotBlank(message = "寄件人姓名不能为空")
    private String senderName;
    @NotBlank(message = "寄件人电话不能为空")
    private String senderPhone;
    @NotBlank(message = "寄件人地址不能为空")
    private String senderAddress;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;

    @NotBlank(message = "收件人姓名不能为空")
    private String receiverName;
    @NotBlank(message = "收件人电话不能为空")
    private String receiverPhone;
    @NotBlank(message = "收件人地址不能为空")
    private String receiverAddress;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;

    @NotNull(message = "重量不能为空")
    @DecimalMin(value = "0.01", message = "重量必须大于0")
    private BigDecimal weight;
    private BigDecimal volume;
    @Min(value = 1, message = "物品数量至少为1")
    private Integer itemCount;
    private String itemDescription;
    private BigDecimal declaredValue;

    private Integer paymentMethod; // 1-在线支付 2-货到付款 3-月结
    private BigDecimal codAmount;
    private Integer serviceType; // 1-标准 2-加急 3-当日达
    private String remark;
    private LocalDateTime expectedDeliveryTime;
}
