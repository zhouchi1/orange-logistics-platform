package com.orange.logistics.notification.entity;

import com.orange.logistics.notification.enums.ChannelType;
import com.orange.logistics.notification.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 通知发送记 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("notification_record")
public class NotificationRecord {

    @Id
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 事件类型（如 ORDER_SHIPPED, ORDER_SIGNED     */
    private String eventType;

    /**
     * 渠道类型
     */
    private ChannelType channelType;

    /**
     * 接收方（手机邮箱/openid等）
     */
    private String receiver;

    /**
     * 发送内     */
    private String content;

    /**
     * 发送状     */
    private NotificationStatus status;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 发送时     */
    private LocalDateTime sentAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
