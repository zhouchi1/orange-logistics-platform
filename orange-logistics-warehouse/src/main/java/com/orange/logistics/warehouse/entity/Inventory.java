package com.orange.logistics.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long warehouseId;
    private Long locationId;
    private String skuCode;
    private String skuName;
    private Integer quantity;
    private Integer lockedQuantity; // 锁定数量（已分配未出库）
    private Integer availableQuantity; // 可用数量
    private String batchNo;
    private LocalDateTime productionDate;
    private LocalDateTime expirationDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
