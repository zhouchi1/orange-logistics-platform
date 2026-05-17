package com.orange.logistics.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("address_book")
public class AddressBook {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long customerId;
    private String contactName;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String street;
    private String detailAddress;
    private String fullAddress;
    private String tag; // 家、公司、学校等
    private Boolean isDefault;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
