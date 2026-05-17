package com.orange.logistics.order.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal weight;
    private BigDecimal volume;
    private Integer itemCount;
    private String itemDescription;
    private BigDecimal freight;
    private Integer paymentMethod;
    private String paymentMethodDesc;
    private BigDecimal codAmount;
    private Integer status;
    private String statusDesc;
    private Integer serviceType;
    private String waybillNo;
    private LocalDateTime expectedDeliveryTime;
    private LocalDateTime createTime;
    private List<OrderStatusLogVO> statusLogs;
}
