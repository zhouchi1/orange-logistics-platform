package com.orange.logistics.risk.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 黑名单实 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("blacklist")
public class Blacklist {

    @Id
    private Long id;

    /**
     * 黑名单类型：USER / PHONE / ADDRESS
     */
    private String type;

    /**
     * 黑名单值（用户ID/手机地址关键词）
     */
    private String value;

    /**
     * 原因
     */
    private String reason;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 过期时间（null表示永久     */
    private LocalDateTime expireAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 创建议     */
    private String createdBy;
}
