package com.orange.logistics.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("bill")
public class Bill {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String billNo; // 账单
    private String orderNo; // 关联订单
    private String waybillNo; // 关联运单
    private Long customerId;
    private BigDecimal weight; // 重量
    private BigDecimal volume; // 体积
    private BigDecimal distance; // 距离
    private Integer serviceType; // 服务类型
    private BigDecimal baseFreight; // 基础运费
    private BigDecimal weightFreight; // 重量
    private BigDecimal distanceSurcharge; // 距离附加
    private BigDecimal serviceMarkup; // 时效加价
    private BigDecimal couponDiscount; // 优惠券抵
    private Long couponId; // 使用的优惠券ID
    private BigDecimal totalFreight; // 总运
    private BigDecimal actualAmount; // 实付金额
    private Integer paymentStatus; // 1-待支2-已支3-已退
    private Integer settlementStatus; // 1-未结算2-已结算
    private LocalDateTime payTime;
    private LocalDateTime settlementTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
