package com.orange.logistics.waybill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("waybill")
public class Waybill {
    @TableId(type = IdType.ASSIGN_ID)
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
    private BigDecimal volume;
    private Integer itemCount;
    private String itemDescription;
    private Integer status; // 0-待揽收 1-已揽收 2-运输中 3-派送中 4-已签收
    private String signType; // PHOTO-拍照签收 SIGNATURE-电子签名 PROXY-代签
    private String signPhotoUrl;
    private String signatureData;
    private String signedBy;
    private LocalDateTime signTime;
    private String currentLocation;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
