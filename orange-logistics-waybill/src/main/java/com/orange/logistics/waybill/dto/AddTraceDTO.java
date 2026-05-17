package com.orange.logistics.waybill.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AddTraceDTO {
    private String waybillNo;
    private String location;
    private String description;
    private String operator;
    private Integer traceType;
    private Double longitude;
    private Double latitude;
    private LocalDateTime traceTime;
}
