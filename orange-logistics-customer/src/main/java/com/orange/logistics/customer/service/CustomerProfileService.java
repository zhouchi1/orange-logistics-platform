package com.orange.logistics.customer.service;

import com.orange.logistics.customer.entity.Customer;
import com.orange.logistics.customer.repository.CustomerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 客户画像服务
 * 基于 RFM 模型计算客户价值评分
 * R = Recency（最近一次下单距今天数）
 * F = Frequency（近90天下单频次）
 * M = Monetary（近90天消费金额）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerMapper customerMapper;
    private final StringRedisTemplate redisTemplate;

    // RFM 权重
    private static final double WEIGHT_R = 0.3;
    private static final double WEIGHT_F = 0.3;
    private static final double WEIGHT_M = 0.4;

    // 评分阈值
    private static final int DIAMOND_THRESHOLD = 85;
    private static final int GOLD_THRESHOLD = 65;
    private static final int SILVER_THRESHOLD = 40;

    /**
     * 计算客户 RFM 评分
     */
    public Map<String, Object> calculateRfmScore(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new RuntimeException("客户不存在");
        }

        // 获取 RFM 原始数据
        int recencyDays = getRecencyDays(customerId);
        int frequency = getFrequency90Days(customerId);
        BigDecimal monetary = getMonetary90Days(customerId);

        // 计算各维度评分（0-100）
        double rScore = calculateRecencyScore(recencyDays);
        double fScore = calculateFrequencyScore(frequency);
        double mScore = calculateMonetaryScore(monetary);

        // 综合评分
        double totalScore = rScore * WEIGHT_R + fScore * WEIGHT_F + mScore * WEIGHT_M;

        // 确定客户等级
        int level = determineLevel(totalScore);
        String levelName = getLevelName(level);

        // 客户标签
        List<String> tags = generateTags(rScore, fScore, mScore, recencyDays, frequency, monetary);

        // 缓存评分到Redis
        String cacheKey = "customer:profile:" + customerId;
        redisTemplate.opsForHash().put(cacheKey, "rfmScore", String.valueOf(totalScore));
        redisTemplate.opsForHash().put(cacheKey, "level", String.valueOf(level));
        redisTemplate.opsForHash().put(cacheKey, "updateTime", LocalDateTime.now().toString());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("customerId", customerId);
        profile.put("customerName", customer.getName());
        profile.put("recencyDays", recencyDays);
        profile.put("recencyScore", round(rScore));
        profile.put("frequency90d", frequency);
        profile.put("frequencyScore", round(fScore));
        profile.put("monetary90d", monetary);
        profile.put("monetaryScore", round(mScore));
        profile.put("totalScore", round(totalScore));
        profile.put("level", level);
        profile.put("levelName", levelName);
        profile.put("tags", tags);
        profile.put("suggestion", generateSuggestion(rScore, fScore, mScore));

        log.info("客户画像计算: {} RFM={}/{}/{} 总分={} 等级={}",
                customer.getName(), round(rScore), round(fScore), round(mScore),
                round(totalScore), levelName);

        return profile;
    }

    /**
     * 批量更新客户等级
     */
    public int batchUpdateLevels() {
        List<Customer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<Customer>().eq(Customer::getStatus, 1));

        int updated = 0;
        for (Customer customer : customers) {
            try {
                Map<String, Object> profile = calculateRfmScore(customer.getId());
                int newLevel = (int) profile.get("level");
                if (newLevel != customer.getLevel()) {
                    customer.setLevel(newLevel);
                    customer.setUpdateTime(LocalDateTime.now());
                    customerMapper.updateById(customer);
                    updated++;
                }
            } catch (Exception e) {
                log.warn("更新客户等级失败: {}", customer.getId(), e);
            }
        }
        log.info("批量更新客户等级完成: 总数={} 更新={}", customers.size(), updated);
        return updated;
    }

    /**
     * 获取客户流失风险
     */
    public Map<String, Object> getChurnRisk(Long customerId) {
        int recencyDays = getRecencyDays(customerId);
        int frequency = getFrequency90Days(customerId);

        double riskScore;
        String riskLevel;
        String suggestion;

        if (recencyDays > 60 && frequency <= 1) {
            riskScore = 90;
            riskLevel = "极高";
            suggestion = "客户已超60天未下单，建议发送专属优惠券召回";
        } else if (recencyDays > 30 && frequency <= 2) {
            riskScore = 70;
            riskLevel = "高";
            suggestion = "客户活跃度下降，建议推送个性化推荐";
        } else if (recencyDays > 14) {
            riskScore = 40;
            riskLevel = "中";
            suggestion = "客户近期不太活跃，可适当关怀";
        } else {
            riskScore = 10;
            riskLevel = "低";
            suggestion = "客户活跃度正常";
        }

        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("customerId", customerId);
        risk.put("riskScore", riskScore);
        risk.put("riskLevel", riskLevel);
        risk.put("lastOrderDays", recencyDays);
        risk.put("recentFrequency", frequency);
        risk.put("suggestion", suggestion);
        return risk;
    }

    // ========== 私有方法 ==========

    private int getRecencyDays(Long customerId) {
        // Redis 缓存获取最近下单时间，没有则查数据库
        String key = "customer:last_order:" + customerId;
        String lastOrderStr = redisTemplate.opsForValue().get(key);
        if (lastOrderStr != null) {
            LocalDateTime lastOrder = LocalDateTime.parse(lastOrderStr);
            return (int) ChronoUnit.DAYS.between(lastOrder, LocalDateTime.now());
        }
        // 模拟：基于客户创建时间和总订单数估算
        Customer customer = customerMapper.selectById(customerId);
        if (customer.getTotalOrders() == 0) {
            return (int) ChronoUnit.DAYS.between(customer.getCreateTime(), LocalDateTime.now());
        }
        // 假设最近有订单，返回合理值
        return Math.min(30, (int) ChronoUnit.DAYS.between(
                customer.getUpdateTime(), LocalDateTime.now()));
    }

    private int getFrequency90Days(Long customerId) {
        String key = "customer:frequency_90d:" + customerId;
        String freq = redisTemplate.opsForValue().get(key);
        if (freq != null) {
            return Integer.parseInt(freq);
        }
        Customer customer = customerMapper.selectById(customerId);
        // 估算：总订单数 / 注册天数 * 90
        long days = ChronoUnit.DAYS.between(customer.getCreateTime(), LocalDateTime.now());
        if (days == 0) return customer.getTotalOrders();
        return (int) Math.min(customer.getTotalOrders(),
                (long) customer.getTotalOrders() * 90 / days);
    }

    private BigDecimal getMonetary90Days(Long customerId) {
        String key = "customer:monetary_90d:" + customerId;
        String amount = redisTemplate.opsForValue().get(key);
        if (amount != null) {
            return new BigDecimal(amount);
        }
        // 基于客户等级估算消费金额
        Customer customer = customerMapper.selectById(customerId);
        int freq = getFrequency90Days(customerId);
        // 平均每单消费 15-50 元运费
        double avgOrder = 15 + customer.getLevel() * 10;
        return BigDecimal.valueOf(freq * avgOrder).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * R 评分：最近下单越近分越高
     */
    private double calculateRecencyScore(int days) {
        if (days <= 1) return 100;
        if (days <= 3) return 90;
        if (days <= 7) return 80;
        if (days <= 14) return 65;
        if (days <= 30) return 45;
        if (days <= 60) return 25;
        if (days <= 90) return 10;
        return 5;
    }

    /**
     * F 评分：下单频次越高分越高
     */
    private double calculateFrequencyScore(int frequency) {
        if (frequency >= 20) return 100;
        if (frequency >= 15) return 85;
        if (frequency >= 10) return 70;
        if (frequency >= 5) return 50;
        if (frequency >= 3) return 35;
        if (frequency >= 1) return 15;
        return 0;
    }

    /**
     * M 评分：消费金额越高分越高
     */
    private double calculateMonetaryScore(BigDecimal monetary) {
        double amount = monetary.doubleValue();
        if (amount >= 5000) return 100;
        if (amount >= 3000) return 85;
        if (amount >= 1000) return 65;
        if (amount >= 500) return 45;
        if (amount >= 100) return 25;
        return 10;
    }

    private int determineLevel(double totalScore) {
        if (totalScore >= DIAMOND_THRESHOLD) return 4; // 钻石
        if (totalScore >= GOLD_THRESHOLD) return 3;    // 金牌
        if (totalScore >= SILVER_THRESHOLD) return 2;  // 银牌
        return 1; // 普通
    }

    private String getLevelName(int level) {
        return switch (level) {
            case 4 -> "钻石会员";
            case 3 -> "金牌会员";
            case 2 -> "银牌会员";
            default -> "普通会员";
        };
    }

    private List<String> generateTags(double r, double f, double m,
                                       int days, int freq, BigDecimal monetary) {
        List<String> tags = new ArrayList<>();
        if (r >= 80 && f >= 70) tags.add("高活跃");
        if (r < 30) tags.add("沉睡用户");
        if (f >= 80) tags.add("高频用户");
        if (m >= 80) tags.add("高价值");
        if (r >= 70 && f >= 50 && m >= 50) tags.add("忠实客户");
        if (r >= 80 && f < 30) tags.add("新客户");
        if (r < 40 && f >= 50) tags.add("流失风险");
        if (monetary.doubleValue() >= 3000) tags.add("大客户");
        if (freq >= 15) tags.add("重度用户");
        if (days <= 3) tags.add("近期活跃");
        return tags;
    }

    private String generateSuggestion(double r, double f, double m) {
        if (r >= 70 && f >= 70 && m >= 70) {
            return "核心客户，保持服务质量，提供专属权益";
        } else if (r >= 70 && f < 40) {
            return "新客户或偶尔使用，引导复购，发放新人优惠券";
        } else if (r < 40 && f >= 50) {
            return "流失风险客户，紧急召回，发送大额优惠券";
        } else if (m >= 70 && f < 40) {
            return "高价值低频客户，提升使用频次，推荐增值服务";
        } else if (r < 30 && f < 30) {
            return "已流失客户，评估召回成本，考虑放弃或大力度召回";
        }
        return "普通客户，常规运营维护";
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
