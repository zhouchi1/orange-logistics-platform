package com.orange.logistics.im.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 发送消息请 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    /**
     * 接收者ID
     */
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
     * 群组ID（群聊时使用     */
    private String groupId;

    /**
     * 扩展字段
     */
    private Map<String, Object> extra;
}
