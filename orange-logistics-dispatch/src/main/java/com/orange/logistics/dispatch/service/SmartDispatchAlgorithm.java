package com.orange.logistics.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能派单算法
 * 多因子评分模型：距离 + 负载 + 时效 + 技能匹配
 *
 * 评分公式：
 * Score = W1 * DistanceScore + W2 * LoadScore + W3 * TimeScore + W4 * SkillScore
 */
@Slf4j
@Component
public class SmartDispatchAlgorithm {

    // 权重配置
    private static final double W_DISTANCE = 0.30;
    private static final double W_LOAD = 0.25;
    private static final double W_TIME = 0.25;
    private static final double W_SKILL = 0.20;

    // 约束
    private static final int MAX_TASKS_PER_COURIER = 30;
    private static final double MAX_DISTANCE_KM = 10.0;

    /**
     * 智能派单：为一个配送任务选择最优快递员
     *
     * @param task     配送任务信息
     * @param couriers 可用快递员列表
     * @return 排序后的快递员推荐列表（评分从高到低）
     */
    public List<Map<String, Object>> dispatch(Map<String, Object> task, List<Map<String, Object>> couriers) {
        if (couriers == null || couriers.isEmpty()) {
            log.warn("无可用快递员");
            return Collections.emptyList();
        }

        double taskLat = toDouble(task.get("latitude"));
        double taskLng = toDouble(task.get("longitude"));
        int urgencyLevel = toInt(task.getOrDefault("urgencyLevel", 1)); // 1普通 2加急 3特急
        String requiredSkill = (String) task.getOrDefault("requiredSkill", "NORMAL");
        // NORMAL, COLD_CHAIN, HEAVY, FRAGILE

        List<Map<String, Object>> scoredCouriers = new ArrayList<>();

        for (Map<String, Object> courier : couriers) {
            double courierLat = toDouble(courier.get("latitude"));
            double courierLng = toDouble(courier.get("longitude"));
            int currentTasks = toInt(courier.getOrDefault("currentTasks", 0));
            int maxTasks = toInt(courier.getOrDefault("maxTasks", MAX_TASKS_PER_COURIER));
            List<String> skills = getSkills(courier);
            double avgDeliveryTime = toDouble(courier.getOrDefault("avgDeliveryMinutes", 30));

            // 1. 距离评分（越近越高）
            double distance = haversineDistance(taskLat, taskLng, courierLat, courierLng);
            double distanceScore;
            if (distance >= MAX_DISTANCE_KM) {
                distanceScore = 0;
            } else {
                distanceScore = (1.0 - distance / MAX_DISTANCE_KM) * 100;
            }

            // 2. 负载评分（任务越少越高）
            double loadScore;
            if (currentTasks >= maxTasks) {
                loadScore = 0; // 已满载，不可派单
            } else {
                loadScore = (1.0 - (double) currentTasks / maxTasks) * 100;
            }

            // 3. 时效评分（基于紧急程度和快递员历史效率）
            double timeScore = calculateTimeScore(urgencyLevel, avgDeliveryTime, distance);

            // 4. 技能匹配评分
            double skillScore = calculateSkillScore(requiredSkill, skills);

            // 综合评分
            double totalScore = W_DISTANCE * distanceScore
                    + W_LOAD * loadScore
                    + W_TIME * timeScore
                    + W_SKILL * skillScore;

            // 过滤不合格的（满载或超距离）
            if (loadScore == 0 || distance > MAX_DISTANCE_KM) {
                continue;
            }

            Map<String, Object> scored = new LinkedHashMap<>();
            scored.put("courierId", courier.get("courierId"));
            scored.put("courierName", courier.get("courierName"));
            scored.put("distance", Math.round(distance * 100.0) / 100.0);
            scored.put("currentTasks", currentTasks);
            scored.put("distanceScore", Math.round(distanceScore * 10.0) / 10.0);
            scored.put("loadScore", Math.round(loadScore * 10.0) / 10.0);
            scored.put("timeScore", Math.round(timeScore * 10.0) / 10.0);
            scored.put("skillScore", Math.round(skillScore * 10.0) / 10.0);
            scored.put("totalScore", Math.round(totalScore * 10.0) / 10.0);
            scored.put("estimatedMinutes", estimateDeliveryTime(distance, avgDeliveryTime));
            scoredCouriers.add(scored);
        }

        // 按总分降序排列
        scoredCouriers.sort((a, b) -> Double.compare(
                toDouble(b.get("totalScore")), toDouble(a.get("totalScore"))));

        log.info("智能派单完成: 任务坐标=({},{}) 候选人={} 推荐={}",
                taskLat, taskLng, couriers.size(), scoredCouriers.size());

        return scoredCouriers;
    }

    /**
     * 批量派单：为多个任务分配快递员（贪心策略）
     */
    public Map<String, Object> batchDispatch(List<Map<String, Object>> tasks,
                                              List<Map<String, Object>> couriers) {
        Map<String, List<Map<String, Object>>> assignments = new LinkedHashMap<>();
        List<Map<String, Object>> unassigned = new ArrayList<>();
        // 复制快递员列表，跟踪动态负载
        List<Map<String, Object>> mutableCouriers = new ArrayList<>();
        for (Map<String, Object> c : couriers) {
            mutableCouriers.add(new HashMap<>(c));
        }

        // 按紧急程度排序（特急优先）
        tasks.sort((a, b) -> toInt(b.getOrDefault("urgencyLevel", 1))
                - toInt(a.getOrDefault("urgencyLevel", 1)));

        for (Map<String, Object> task : tasks) {
            List<Map<String, Object>> ranked = dispatch(task, mutableCouriers);
            if (!ranked.isEmpty()) {
                Map<String, Object> best = ranked.get(0);
                String courierId = best.get("courierId").toString();

                assignments.computeIfAbsent(courierId, k -> new ArrayList<>()).add(task);

                // 更新快递员负载
                for (Map<String, Object> c : mutableCouriers) {
                    if (courierId.equals(c.get("courierId").toString())) {
                        c.put("currentTasks", toInt(c.get("currentTasks")) + 1);
                        break;
                    }
                }
            } else {
                unassigned.add(task);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTasks", tasks.size());
        result.put("assigned", tasks.size() - unassigned.size());
        result.put("unassigned", unassigned.size());
        result.put("assignments", assignments);
        result.put("unassignedTasks", unassigned);

        log.info("批量派单完成: 总任务={} 已分配={} 未分配={}",
                tasks.size(), tasks.size() - unassigned.size(), unassigned.size());
        return result;
    }

    // ========== 评分计算 ==========
    private double calculateTimeScore(int urgencyLevel, double avgDeliveryMinutes, double distance) {
        // 预估配送时间
        double estimatedMinutes = estimateDeliveryTime(distance, avgDeliveryMinutes);

        // 时效要求（分钟）
        double deadline = switch (urgencyLevel) {
            case 3 -> 60;   // 特急：1小时
            case 2 -> 120;  // 加急：2小时
            default -> 240; // 普通：4小时
        };

        // 剩余时间充裕度
        double margin = (deadline - estimatedMinutes) / deadline;
        return Math.max(0, Math.min(100, margin * 100));
    }

    private double calculateSkillScore(String required, List<String> courierSkills) {
        if ("NORMAL".equals(required)) {
            return 80; // 普通任务，所有人都能接
        }
        if (courierSkills.contains(required)) {
            return 100; // 完全匹配
        }
        // 部分匹配（如有相关经验）
        if (courierSkills.contains("ALL") || courierSkills.size() >= 3) {
            return 60; // 多技能快递员
        }
        return 20; // 不匹配但可以尝试
    }

    private int estimateDeliveryTime(double distanceKm, double avgMinutes) {
        // 基于距离估算：骑行速度15km/h + 上楼/等待时间
        double travelMinutes = (distanceKm / 15.0) * 60;
        double serviceMinutes = 5; // 上楼、签收等
        return (int) Math.ceil(travelMinutes + serviceMinutes);
    }

    // ========== 工具方法 ==========

    /**
     * Haversine 公式计算两点间距离（km）
     */
    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371; // 地球半径 km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @SuppressWarnings("unchecked")
    private List<String> getSkills(Map<String, Object> courier) {
        Object skills = courier.get("skills");
        if (skills instanceof List) {
            return (List<String>) skills;
        }
        if (skills instanceof String) {
            return Arrays.asList(((String) skills).split(","));
        }
        return List.of("NORMAL");
    }

    private double toDouble(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0; }
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }
}
