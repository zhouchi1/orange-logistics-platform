package com.orange.logistics.transport.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("temperature_record")
public class TemperatureRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long transportOrderId;
    private String transportNo;
    private Long vehicleId;
    private String plateNumber;
    private BigDecimal temperature; // 当前温度
    private BigDecimal humidity; // 湿度
    private BigDecimal setMinTemp; // 设定最低温控
    private BigDecimal setMaxTemp; // 设定最高温控
    private Boolean alarm; // 是否报警
    private String alarmReason; // 报警原因
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
