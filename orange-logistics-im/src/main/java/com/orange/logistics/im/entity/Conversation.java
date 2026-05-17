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
 * 会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("im_conversation")
public class Conversation {

    @Id
    private Long id;

    /**
     * 会话ID
     */
    @Column("conversation_id")
    private String conversationId;

    /**
     * 会话类型：PRIVATE, GROUP
     */
    @Column("conversation_type")
    private String conversationType;

    /**
     * 用户ID（会话所属用户）
     */
    @Column("user_id")
    private String userId;

    /**
     * 对方ID（单聊为对方用户ID，群聊为群组ID     */
    @Column("target_id")
    private String targetId;

    /**
     * 最后一条消息内     */
    @Column("last_message")
    private String lastMessage;

    /**
     * 最后一条消息时     */
    @Column("last_message_time")
    private LocalDateTime lastMessageTime;

    /**
     * 未读消息     */
    @Column("unread_count")
    private Integer unreadCount;

    /**
     * 是否置顶
     */
    @Column("pinned")
    private Boolean pinned;

    /**
     * 是否免打     */
    @Column("muted")
    private Boolean muted;

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
