package com.orange.logistics.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("logistics_order")
public class LogisticsOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long customerId;
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private BigDecimal weight;
    private BigDecimal volume;
    private Integer itemCount;
    private String itemDescription;
    private BigDecimal declaredValue;
    private BigDecimal freight;
    private Integer paymentMethod; // 1-在线支付 2-货到付款 3-月结
    private BigDecimal codAmount; // 货到付款金额
    private Integer status;
    private Integer serviceType; // 1-标准 2-加急 3-当日达
    private String remark;
    private Long parentOrderId; // 拆分订单的父订单ID
    private String waybillNo; // 关联运单号
    private LocalDateTime expectedDeliveryTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
