package com.orange.logistics.dispatch.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("courier")
public class Courier {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String courierCode; // 快递员编号
    private String name;
    private String phone;
    private String idCard;
    private Long stationId; // 所属站点ID
    private String stationName;
    private String area; // 负责区域
    private BigDecimal longitude; // 当前经度
    private BigDecimal latitude; // 当前纬度
    private Integer currentLoad; // 当前负载(件数)
    private Integer maxLoad; // 最大负
    private Integer status; // 1-空闲 2-配送中 3-休息 4-离职
    private BigDecimal rating; // 评分
    private Integer totalDeliveries; // 总配送量
    private Integer todayDeliveries; // 今日配送量
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
