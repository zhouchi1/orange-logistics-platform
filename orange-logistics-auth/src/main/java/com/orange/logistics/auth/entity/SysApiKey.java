package com.orange.logistics.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_api_key")
public class SysApiKey {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String apiKey;
    private String apiSecret;
    private String name;
    private Integer status; // 0-禁用 1-启用
    private LocalDateTime expireTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
