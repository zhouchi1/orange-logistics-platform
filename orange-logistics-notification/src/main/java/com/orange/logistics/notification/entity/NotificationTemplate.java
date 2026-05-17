package com.orange.logistics.notification.entity;

import com.orange.logistics.notification.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 通知模板实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("notification_template")
public class NotificationTemplate {

    @Id
    private Long id;

    /**
     * 模板编码（唯一标识     */
    private String templateCode;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 渠道类型
     */
    private ChannelType channelType;

    /**
     * 模板内容，支持占位符 ${variable}
     */
    private String content;

    /**
     * 模板标题（邮推送使用）
     */
    private String title;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private java.time.LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private java.time.LocalDateTime updatedAt;
}
