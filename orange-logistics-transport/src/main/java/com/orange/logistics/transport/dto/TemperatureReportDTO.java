package com.orange.logistics.transport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TemperatureReportDTO {
    @NotNull(message = "运输单ID不能为空")
    private Long transportOrderId;
    @NotNull(message = "车辆ID不能为空")
    private Long vehicleId;
    @NotNull(message = "温度不能为空")
    private BigDecimal temperature;
    private BigDecimal humidity;
    private BigDecimal setMinTemp;
    private BigDecimal setMaxTemp;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime recordTime;
}
