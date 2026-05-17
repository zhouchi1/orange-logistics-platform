package com.orange.logistics.order.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    CREATED(0, "已创建"),
    PENDING_PICKUP(1, "待揽收"),
    PICKED_UP(2, "已揽收"),
    IN_TRANSIT(3, "运输中"),
    DELIVERING(4, "派送中"),
    SIGNED(5, "已签收"),
    COMPLETED(6, "已完成"),
    CANCELLED(7, "已取消"),
    EXCEPTION(8, "异常");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status code: " + code);
    }

    /**
     * 状态机：验证状态转换是否合法
     */
    public boolean canTransitTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PENDING_PICKUP || target == CANCELLED;
            case PENDING_PICKUP -> target == PICKED_UP || target == CANCELLED;
            case PICKED_UP -> target == IN_TRANSIT || target == EXCEPTION;
            case IN_TRANSIT -> target == DELIVERING || target == EXCEPTION;
            case DELIVERING -> target == SIGNED || target == EXCEPTION;
            case SIGNED -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
            case EXCEPTION -> target == IN_TRANSIT || target == DELIVERING || target == CANCELLED;
        };
    }
}
