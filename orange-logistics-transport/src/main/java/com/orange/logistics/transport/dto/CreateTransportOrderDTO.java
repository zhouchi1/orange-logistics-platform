package com.orange.logistics.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateTransportOrderDTO {
    private String waybillNo;
    @NotNull(message = "车辆ID不能为空")
    private Long vehicleId;
    private Long driverId;
    @NotBlank(message = "起始站点不能为空")
    private String originStation;
    private String originAddress;
    private BigDecimal originLongitude;
    private BigDecimal originLatitude;
    @NotBlank(message = "目的站点不能为空")
    private String destStation;
    private String destAddress;
    private BigDecimal destLongitude;
    private BigDecimal destLatitude;
    private String routeId;
    private String cargoDescription;
    private BigDecimal cargoWeight;
    private Integer cargoCount;
    private LocalDateTime plannedDepartTime;
    private LocalDateTime plannedArriveTime;
    private String remark;
}
