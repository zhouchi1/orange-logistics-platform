package com.orange.logistics.transport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("gps_record")
public class GpsRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long vehicleId;
    private String plateNumber;
    private Long transportOrderId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal speed; // km/h
    private BigDecimal direction; // 方向角度 0-360
    private String location; // 位置描述
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
