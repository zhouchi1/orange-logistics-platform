package com.orange.logistics.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String username;
    private String operation;
    private String method;
    private String path;
    private String params;
    private String ip;
    private Integer status; // 0-失败 1-成功
    private Long duration;
    private String errorMsg;
    private LocalDateTime createTime;
}
