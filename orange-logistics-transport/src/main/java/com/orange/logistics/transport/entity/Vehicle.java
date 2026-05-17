package com.orange.logistics.transport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vehicle")
public class Vehicle {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String plateNumber;
    private String vehicleType; // 厢式货车、冷藏车、平板车、挂
    private BigDecimal maxLoad; // 最大载
    private BigDecimal maxVolume; // 最大容立方
    private String fleetName; // 所属车
    private Long driverId; // 当前司机ID
    private String driverName;
    private String driverPhone;
    private BigDecimal longitude; // GPS经度
    private BigDecimal latitude; // GPS纬度
    private String currentLocation; // 当前位置描述
    private Integer status; // 1-空闲 2-运输3-维修4-停用
    private BigDecimal mileage; // 总里km)
    private LocalDateTime lastMaintenanceTime;
    private LocalDateTime nextMaintenanceTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
