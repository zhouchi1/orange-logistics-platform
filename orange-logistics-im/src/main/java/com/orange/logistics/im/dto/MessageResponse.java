package com.orange.logistics.im.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private String messageId;
    private String fromUserId;
    private String fromUserName;
    private String toId;
    private String groupId;
    private String content;
    private String contentType;
    private String status;
    private Boolean recalled;
    private Map<String, Object> extra;
    private LocalDateTime createdAt;
}
