package com.orange.logistics.transport.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransportStatus {
    PENDING(1, "待发车"),
    IN_TRANSIT(2, "运输中"),
    ARRIVED(3, "已到达"),
    EXCEPTION(4, "异常"),
    CANCELLED(5, "已取消");

    private final int code;
    private final String desc;

    public static TransportStatus fromCode(int code) {
        for (TransportStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知运输状态: " + code);
    }
}
