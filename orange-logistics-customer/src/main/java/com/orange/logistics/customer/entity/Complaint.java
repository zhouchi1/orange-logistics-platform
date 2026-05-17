package com.orange.logistics.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("complaint")
public class Complaint {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String complaintNo; // 工单
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String orderNo; // 关联订单
    private Integer type; // 1-延误 2-破损 3-丢失 4-服务态度 5-其他
    private String title;
    private String description;
    private String attachments; // 附件URL列表JSON
    private Integer status; // 1-待处2-处理器3-已解4-已关
    private String handler; // 处理器
    private String handleResult; // 处理结果
    private LocalDateTime handleTime;
    private Integer satisfaction; // 满意1-5
    private String satisfactionComment;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
