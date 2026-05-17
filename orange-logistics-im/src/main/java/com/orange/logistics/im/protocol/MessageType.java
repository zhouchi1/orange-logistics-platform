package com.orange.logistics.im.protocol;

/**
 * 消息类型枚举
 */
public enum MessageType {

    /**
     * 认证消息
     */
    AUTH,

    /**
     * 单聊消息
     */
    CHAT,

    /**
     * 群聊消息
     */
    GROUP_CHAT,

    /**
     * 消息确认（已读回执）
     */
    ACK,

    /**
     * 心跳
     */
    HEARTBEAT,

    /**
     * 系统通知
     */
    NOTIFY,

    /**
     * 消息撤回
     */
    RECALL,

    /**
     * 正在输入
     */
    TYPING,

    /**
     * 上线通知
     */
    ONLINE,

    /**
     * 下线通知
     */
    OFFLINE
}
