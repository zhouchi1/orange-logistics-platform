package com.orange.logistics.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("warehouse")
public class Warehouse {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private String province;
    private String city;
    private String district;
    private String address;
    private Double longitude;
    private Double latitude;
    private Integer totalArea; // 总面平方
    private Integer usedArea;
    private Integer type; // 1-普通仓 2-冷链3-保税
    private Integer status; // 0-停用 1-启用
    private String contactName;
    private String contactPhone;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
