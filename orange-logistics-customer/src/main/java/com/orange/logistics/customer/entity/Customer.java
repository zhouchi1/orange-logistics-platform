package com.orange.logistics.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String customerCode;
    private String name;
    private String phone;
    private String email;
    private Integer level; // 1-普2-银牌 3-金牌 4-钻石
    private Integer points; // 积分
    private Integer totalOrders; // 总订单数
    private String defaultAddressId;
    private Integer status; // 1-正常 2-冻结
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
