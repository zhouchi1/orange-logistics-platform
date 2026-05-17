package com.orange.logistics.waybill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("waybill_trace")
public class WaybillTrace {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long waybillId;
    private String waybillNo;
    private String location;
    private String description;
    private String operator;
    private Integer traceType; // 1-揽收 2-到达 3-发出 4-派5-签收 6-异常
    private Double longitude;
    private Double latitude;
    private LocalDateTime traceTime;
    private LocalDateTime createTime;
}
