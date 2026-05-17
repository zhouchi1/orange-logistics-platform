package com.orange.logistics.model.enums;

/**
 * 物流状态枚举
 */
public enum LogisticsStatus {
    PICKED_UP("揽收", 1),
    IN_TRANSIT("运输中", 2),
    AT_TRANSFER("中转", 3),
    OUT_FOR_DELIVERY("派送中", 4),
    DELIVERED("已签收", 5),
    EXCEPTION("异常", 6);

    private final String description;
    private final int order;

    LogisticsStatus(String description, int order) {
        this.description = description;
        this.order = order;
    }

    public String getDescription() {
        return description;
    }

    public int getOrder() {
        return order;
    }

    public static LogisticsStatus fromOrder(int order) {
        for (LogisticsStatus status : values()) {
            if (status.order == order) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order: " + order);
    }
}
