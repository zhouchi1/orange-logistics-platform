package com.orange.logistics.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class VehicleDTO {
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;
    @NotBlank(message = "车型不能为空")
    private String vehicleType;
    @NotNull(message = "最大载重不能为空")
    private BigDecimal maxLoad;
    private BigDecimal maxVolume;
    private String fleetName;
    private Long driverId;
    private String driverName;
    private String driverPhone;
}
