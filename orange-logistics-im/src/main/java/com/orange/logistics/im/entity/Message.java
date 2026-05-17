package com.orange.logistics.im.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("im_message")
public class Message {

    @Id
    private Long id;

    /**
     * 消息ID（雪花算法）
     */
    @Column("message_id")
    private String messageId;

    /**
     * 消息类型：CHAT, GROUP_CHAT
     */
    @Column("message_type")
    private String messageType;

    /**
     * 发送者ID
     */
    @Column("from_user_id")
    private String fromUserId;

    /**
     * 发送者名单     */
    @Column("from_user_name")
    private String fromUserName;

    /**
     * 接收者ID
     */
    @Column("to_id")
    private String toId;

    /**
     * 群组ID（群聊消息）
     */
    @Column("group_id")
    private String groupId;

    /**
     * 消息内容
     */
    @Column("content")
    private String content;

    /**
     * 内容类型：TEXT, IMAGE, VOICE, FILE, LOCATION, LOGISTICS_CARD
     */
    @Column("content_type")
    private String contentType;

    /**
     * 扩展信息（JSON     */
    @Column("extra")
    private String extra;

    /**
     * 消息状态：SENT, DELIVERED, READ
     */
    @Column("status")
    private String status;

    /**
     * 是否撤回
     */
    @Column("recalled")
    private Boolean recalled;

    /**
     * 创建时间
     */
    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
