package com.orange.logistics.notification.dto;

import com.orange.logistics.notification.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 发送通知请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 接收方（手机邮箱/openid     */
    private String receiver;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 模板编码
     */
    private String templateCode;

    /**
     * 发送渠道列表（支持多渠道同时发送）
     */
    private List<ChannelType> channels;

    /**
     * 模板变量
     */
    private Map<String, String> variables;
}
