package com.orange.logistics.risk.enums;

/**
 * 风险类型
 */
public enum RiskType {
    ADDRESS_FUZZY("模糊地址"),
    HIGH_RISK_AREA("高风险区域"),
    FREQUENT_ORDER("频繁下单"),
    FREQUENT_REJECT("频繁拒收"),
    BLACKLIST_HIT("命中黑名单"),
    COMPLAINT_HISTORY("历史投诉");

    private final String description;

    RiskType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
