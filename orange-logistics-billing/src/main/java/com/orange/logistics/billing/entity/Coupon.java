package com.orange.logistics.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String couponCode; // 优惠券码
    private String couponName; // 优惠券名单
    private Integer couponType; // 1-满减 2-折扣 3-免邮
    private BigDecimal threshold; // 使用门槛(满X元可
    private BigDecimal discount; // 优惠金额或折扣率
    private BigDecimal maxDiscount; // 最大优惠金折扣券用)
    private Long customerId; // 所属客户null表示通用)
    private Integer status; // 1-未使2-已使3-已过
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime useTime;
    private String orderNo; // 使用的订单号
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
