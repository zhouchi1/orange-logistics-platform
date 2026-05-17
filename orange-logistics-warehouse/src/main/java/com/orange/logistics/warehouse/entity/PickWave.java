package com.orange.logistics.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("pick_wave")
public class PickWave {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String waveNo;
    private Long warehouseId;
    private Integer status; // 0-待执1-执行2-已完
    private Integer orderCount;
    private Integer totalItems;
    private String operator;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
