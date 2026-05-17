package com.orange.logistics.waybill.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WaybillVO {
    private Long id;
    private String waybillNo;
    private Long orderId;
    private String orderNo;
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal weight;
    private Integer status;
    private String statusDesc;
    private String signType;
    private String signPhotoUrl;
    private String signedBy;
    private LocalDateTime signTime;
    private String currentLocation;
    private LocalDateTime createTime;
    private List<WaybillTraceVO> traces;
}
