package com.orange.logistics.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inbound_order")
public class InboundOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String inboundNo;
    private Long warehouseId;
    private Integer type; // 1-采购入库 2-退货入3-调拨入库
    private Integer status; // 0-待收1-收货2-已完
    private String supplierName;
    private Integer totalQuantity;
    private Integer receivedQuantity;
    private String operator;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
