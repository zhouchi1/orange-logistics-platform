package com.orange.logistics.transport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transport_route")
public class TransportRoute {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String routeCode; // 线路编码
    private String routeName; // 线路名称
    private String originStation;
    private String originCity;
    private BigDecimal originLongitude;
    private BigDecimal originLatitude;
    private String destStation;
    private String destCity;
    private BigDecimal destLongitude;
    private BigDecimal destLatitude;
    private BigDecimal distance; // 距离(km)
    private Integer estimatedHours; // 预计耗时(小时)
    private String waypoints; // 途经点JSON
    private Integer status; // 1-启用 2-停用
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
