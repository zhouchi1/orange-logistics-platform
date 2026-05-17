package com.orange.logistics.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("warehouse_location")
public class WarehouseLocation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long warehouseId;
    private String locationCode; // 库位编码 A-01-02-03 (
    private String zone; // 区域
    private String aisle; // 通道
    private String shelf; // 货架
    private Integer layer; // 
    private Integer type; // 1-存储2-拣货3-暂存
    private Integer status; // 0-空闲 1-占用 2-锁定
    private Integer maxWeight; // 最大承kg)
    private Integer maxVolume; // 最大容立方厘米)
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
