package com.orange.logistics.notification.enums;

/**
 * 通知渠道类型
 */
public enum ChannelType {
    SMS("短信"),
    PUSH("APP推送"),
    WECHAT("微信"),
    EMAIL("邮件");

    private final String description;

    ChannelType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
