package com.orange.logistics.transport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RouteDTO {
    @NotBlank(message = "线路编码不能为空")
    private String routeCode;
    @NotBlank(message = "线路名称不能为空")
    private String routeName;
    @NotBlank(message = "起始站点不能为空")
    private String originStation;
    private String originCity;
    private BigDecimal originLongitude;
    private BigDecimal originLatitude;
    @NotBlank(message = "目的站点不能为空")
    private String destStation;
    private String destCity;
    private BigDecimal destLongitude;
    private BigDecimal destLatitude;
    private BigDecimal distance;
    private Integer estimatedHours;
    private String waypoints;
    private String remark;
}
