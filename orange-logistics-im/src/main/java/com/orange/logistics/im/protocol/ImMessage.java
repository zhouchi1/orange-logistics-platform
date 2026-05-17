package com.orange.logistics.im.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * IM 消息协议实体
 * 统一的消息格式，用于 WebSocket 通信
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImMessage {

    /**
     * 消息ID（雪花算法生成）
     */
    private String messageId;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 发送者ID
     */
    private String fromUserId;

    /**
     * 发送者名单     */
    private String fromUserName;

    /**
     * 接收者ID（单聊时为用户ID，群聊时为群组ID     */
    private String toId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 内容类型：TEXT, IMAGE, VOICE, FILE, LOCATION, LOGISTICS_CARD
     */
    private String contentType;

    /**
     * 时间     */
    private Long timestamp;

    /**
     * 扩展字段（如图片URL、文件信息、位置信息等     */
    private Map<String, Object> extra;

    /**
     * 认证 Token（仅 AUTH 类型使用     */
    private String token;

    /**
     * 消息状态：SENT, DELIVERED, READ
     */
    private String status;

    /**
     * 群组ID（群聊消息使用）
     */
    private String groupId;
}
