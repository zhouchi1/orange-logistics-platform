package com.orange.logistics.transport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transport_order")
public class TransportOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String transportNo; // 运输单号
    private String waybillNo; // 关联运单
    private Long vehicleId;
    private String plateNumber;
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private String originStation; // 起始站点
    private String originAddress;
    private BigDecimal originLongitude;
    private BigDecimal originLatitude;
    private String destStation; // 目的站点
    private String destAddress;
    private BigDecimal destLongitude;
    private BigDecimal destLatitude;
    private BigDecimal distance; // 预计距离(km)
    private BigDecimal actualDistance; // 实际距离
    private Integer status; // 1-待发2-运输3-已到4-异常 5-已取
    private LocalDateTime plannedDepartTime;
    private LocalDateTime actualDepartTime;
    private LocalDateTime plannedArriveTime;
    private LocalDateTime actualArriveTime;
    private String routeId; // 线路ID
    private String cargoDescription;
    private BigDecimal cargoWeight;
    private Integer cargoCount;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
