package com.orange.logistics.dispatch.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("delivery_task")
public class DeliveryTask {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String taskNo; // 配送任务号
    private Long courierId;
    private String courierName;
    private String courierPhone;
    private String waybillNo; // 关联运单
    private String orderNo; // 关联订单
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal receiverLongitude;
    private BigDecimal receiverLatitude;
    private Integer deliveryMethod; // 1-上门 2-驿站 3-自提
    private String deliveryPoint; // 驿站/自提柜名单
    private Integer status; // 1-待取2-配送中 3-已签4-拒收 5-异常
    private Integer priority; // 优先1-普2-加3-当日
    private LocalDateTime assignTime; // 派单时间
    private LocalDateTime pickupTime; // 取件时间
    private LocalDateTime deliveryTime; // 送达时间
    private LocalDateTime signTime; // 签收时间
    private String signPhoto; // 签收照片URL
    private String signName; // 签收
    private String remark;
    private BigDecimal dispatchScore; // 派单评分
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
