package com.orange.logistics.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CourierDTO {
    @NotBlank(message = "快递员编号不能为空")
    private String courierCode;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String idCard;
    private Long stationId;
    private String stationName;
    private String area;
    @NotNull(message = "最大负载不能为空")
    private Integer maxLoad;
}
