package com.orange.logistics.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("freight_rule")
public class FreightRule {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String ruleName; // 规则名称
    private String ruleCode; // 规则编码
    private String originRegion; // 始发区域
    private String destRegion; // 目的区域
    private BigDecimal firstWeight; // 首重(kg)
    private BigDecimal firstPrice; // 首重价格
    private BigDecimal additionalWeight; // 续重单位(kg)
    private BigDecimal additionalPrice; // 续重单价
    private BigDecimal distanceSurcharge; // 距离附加km)
    private BigDecimal expressMarkup; // 加急加价比.5表示0%)
    private BigDecimal sameDayMarkup; // 当日达加价比
    private BigDecimal minFreight; // 最低运
    private Integer serviceType; // 适用服务类型 1-标准 2-加3-当日0-通用
    private Integer status; // 1-启用 2-停用
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
