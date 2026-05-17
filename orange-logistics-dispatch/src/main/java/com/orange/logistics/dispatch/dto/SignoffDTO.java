package com.orange.logistics.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SignoffDTO {
    @NotNull(message = "任务ID不能为空")
    private Long taskId;
    private String signName; // 签收人姓
    private String signPhoto; // 签收照片URL
    private Boolean accepted; // true=签收 false=拒收
    private String remark;
}
