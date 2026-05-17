package com.orange.logistics.transport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GpsReportDTO {
    @NotNull(message = "车辆ID不能为空")
    private Long vehicleId;
    private Long transportOrderId;
    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;
    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;
    private BigDecimal speed;
    private BigDecimal direction;
    private String location;
    private LocalDateTime recordTime;
}
