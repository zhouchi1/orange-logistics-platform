package com.orange.logistics.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private String path;
    private String method; // GET, POST, PUT, DELETE
    private Integer type; // 1-菜单 2-按钮 3-API
    private Integer sort;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
