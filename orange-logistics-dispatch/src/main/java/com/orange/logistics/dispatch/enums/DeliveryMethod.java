package com.orange.logistics.dispatch.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeliveryMethod {
    DOOR(1, "上门配送"),
    STATION(2, "驿站代收"),
    LOCKER(3, "自提柜");

    private final int code;
    private final String desc;

    public static DeliveryMethod fromCode(int code) {
        for (DeliveryMethod m : values()) {
            if (m.code == code) return m;
        }
        throw new IllegalArgumentException("未知配送方式: " + code);
    }
}
