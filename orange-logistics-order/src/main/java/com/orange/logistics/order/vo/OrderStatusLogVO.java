package com.orange.logistics.order.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderStatusLogVO {
    private Integer fromStatus;
    private String fromStatusDesc;
    private Integer toStatus;
    private String toStatusDesc;
    private String operator;
    private String remark;
    private LocalDateTime createTime;
}
