package com.orange.logistics.notification.dto;

import com.orange.logistics.notification.enums.ChannelType;
import com.orange.logistics.notification.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知发送响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 是否全部成功
     */
    private Boolean success;

    /**
     * 各渠道发送结果
     */
    private List<ChannelResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelResult {
        private ChannelType channel;
        private NotificationStatus status;
        private String message;
        private LocalDateTime sentAt;
    }
}
