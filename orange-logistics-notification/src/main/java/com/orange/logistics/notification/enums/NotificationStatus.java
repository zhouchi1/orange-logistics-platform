package com.orange.logistics.notification.enums;

/**
 * 通知发送状态
 */
public enum NotificationStatus {
    PENDING("待发送"),
    SENDING("发送中"),
    SUCCESS("发送成功"),
    FAILED("发送失败"),
    THROTTLED("频率限制");

    private final String description;

    NotificationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
