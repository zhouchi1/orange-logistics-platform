package com.orange.logistics.transport.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VehicleStatus {
    IDLE(1, "空闲"),
    IN_TRANSIT(2, "运输中"),
    MAINTENANCE(3, "维修中"),
    DISABLED(4, "停用");

    private final int code;
    private final String desc;

    public static VehicleStatus fromCode(int code) {
        for (VehicleStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知车辆状态: " + code);
    }
}
