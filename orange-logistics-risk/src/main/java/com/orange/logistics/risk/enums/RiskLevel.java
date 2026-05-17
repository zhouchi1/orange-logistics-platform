package com.orange.logistics.risk.enums;

/**
 * 风险等级
 */
public enum RiskLevel {
    LOW("低风险", 0, 30),
    MEDIUM("中风险", 31, 60),
    HIGH("高风险", 61, 85),
    CRITICAL("极高风险", 86, 100);

    private final String description;
    private final int minScore;
    private final int maxScore;

    RiskLevel(String description, int minScore, int maxScore) {
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据分数获取风险等级
     */
    public static RiskLevel fromScore(int score) {
        for (RiskLevel level : values()) {
            if (score >= level.minScore && score <= level.maxScore) {
                return level;
            }
        }
        return CRITICAL;
    }
}
