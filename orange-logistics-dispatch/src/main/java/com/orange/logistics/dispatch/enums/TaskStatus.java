package com.orange.logistics.dispatch.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskStatus {
    PENDING_PICKUP(1, "待取件"),
    DELIVERING(2, "配送中"),
    SIGNED(3, "已签收"),
    REJECTED(4, "拒收"),
    EXCEPTION(5, "异常");

    private final int code;
    private final String desc;

    public static TaskStatus fromCode(int code) {
        for (TaskStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知任务状态: " + code);
    }
}
