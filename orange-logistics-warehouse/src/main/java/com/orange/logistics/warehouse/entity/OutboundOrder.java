package com.orange.logistics.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("outbound_order")
public class OutboundOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String outboundNo;
    private Long warehouseId;
    private Long orderId;
    private String orderNo;
    private Integer type; // 1-销售出2-调拨出库 3-报废出库
    private Integer status; // 0-待拣1-拣货2-已拣3-已出
    private Integer totalQuantity;
    private Integer pickedQuantity;
    private Long waveId; // 波次ID
    private String operator;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
