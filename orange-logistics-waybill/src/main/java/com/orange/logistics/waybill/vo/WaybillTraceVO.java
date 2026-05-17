package com.orange.logistics.waybill.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WaybillTraceVO {
    private String location;
    private String description;
    private String operator;
    private Integer traceType;
    private String traceTypeDesc;
    private LocalDateTime traceTime;
}
