package com.orange.logistics.model.enums;

/**
 * 异常类型枚举
 */
public enum AnomalyType {
    TIMEOUT_NO_UPDATE("超时未更新", "包裹在某个状态停留时间超过阈值"),
    ROUTE_LOOP("路由回环", "包裹经过了重复的中转站"),
    DELIVERY_DELAY("派送延迟", "派送时间超过预期"),
    STATUS_REGRESSION("状态回退", "物流状态出现逆向变化"),
    LOCATION_ANOMALY("位置异常", "包裹位置与预期路线偏差过大");

    private final String name;
    private final String description;

    AnomalyType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
