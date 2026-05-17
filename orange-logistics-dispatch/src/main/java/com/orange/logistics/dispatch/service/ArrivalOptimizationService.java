package com.orange.logistics.dispatch.service;

import com.orange.logistics.dispatch.client.AiPredictionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 到货时间优化服务
 * 集成 AI 预测，为司机生成最优配送计划
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArrivalOptimizationService {

    private final AiPredictionClient aiPredictionClient;

    /**
     * 获取门店最优到货时间建议
     *
     * @param storeId        门店ID
     * @param inventoryRatio 当前库存比率 (0-1)
     * @return 最优到货时间分析结果
     */
    public Map<String, Object> getOptimalArrivalTime(String storeId, double inventoryRatio) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("store_id", storeId);
            request.put("inventory_ratio", inventoryRatio);
            request.put("target_date", LocalDate.now().plusDays(1).toString());

            Map<String, Object> result = aiPredictionClient.analyzeSingleStore(request);
            log.info("门店{}最优到货分析完成: {}", storeId, result.get("recommendation"));
            return result;
        } catch (Exception e) {
            log.error("调用AI预测服务失败, storeId={}: {}", storeId, e.getMessage());
            return getDefaultRecommendation(storeId);
        }
    }

    /**
     * 批量获取门店最优到货时间
     *
     * @param storeIds      门店ID列表
     * @param inventoryData 库存数据 {storeId: ratio}
     * @return 批量分析结果
     */
    public Map<String, Object> batchAnalyze(List<String> storeIds, Map<String, Double> inventoryData) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("store_ids", storeIds);
            request.put("target_date", LocalDate.now().plusDays(1).toString());
            if (inventoryData != null) {
                request.put("inventory_data", inventoryData);
            }

            return aiPredictionClient.analyzeBatch(request);
        } catch (Exception e) {
            log.error("批量分析失败: {}", e.getMessage());
            return Map.of("total", 0, "results", Collections.emptyList(), "error", e.getMessage());
        }
    }

    /**
     * 生成司机配送计划
     * 综合考虑各门店最优到货时间，生成按时间排序的配送路线
     *
     * @param driverId      司机ID
     * @param storeIds      需要配送的门店列表
     * @param inventoryData 各门店库存
     * @return 配送计划（含排序、时间窗、收益预估）
     */
    public Map<String, Object> generateDeliveryPlan(
            String driverId,
            List<String> storeIds,
            Map<String, Double> inventoryData) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("driver_id", driverId);
            request.put("store_ids", storeIds);
            request.put("target_date", LocalDate.now().plusDays(1).toString());
            request.put("departure_time", "06:00");
            if (inventoryData != null) {
                request.put("inventory_data", inventoryData);
            }

            Map<String, Object> plan = aiPredictionClient.generateDriverSchedule(request);
            log.info("司机{}配送计划生成完成, 门店数={}, 预期收益增量={}",
                    driverId, storeIds.size(), plan.get("total_potential_revenue_gain"));
            return plan;
        } catch (Exception e) {
            log.error("生成配送计划失败, driverId={}: {}", driverId, e.getMessage());
            return getDefaultPlan(driverId, storeIds);
        }
    }

    /**
     * 获取门店销售曲线（用于前端展示）
     */
    public Map<String, Object> getStoreRevenueCurve(String storeId, Integer dayOfWeek) {
        try {
            return aiPredictionClient.getRevenueCurve(storeId, dayOfWeek);
        } catch (Exception e) {
            log.error("获取销售曲线失败, storeId={}: {}", storeId, e.getMessage());
            return Map.of("store_id", storeId, "error", e.getMessage());
        }
    }

    // ========== Fallback ==========

    private Map<String, Object> getDefaultRecommendation(String storeId) {
        Map<String, Object> result = new HashMap<>();
        result.put("store_id", storeId);
        result.put("optimal_arrival", Map.of("hour", 8, "window_start", "07:00", "window_end", "09:00"));
        result.put("confidence", 0.1);
        result.put("recommendation", "AI服务不可用，使用默认建议: 07:00-09:00到货");
        return result;
    }

    private Map<String, Object> getDefaultPlan(String driverId, List<String> storeIds) {
        List<Map<String, Object>> schedule = new ArrayList<>();
        int seq = 1;
        for (String storeId : storeIds) {
            Map<String, Object> item = new HashMap<>();
            item.put("sequence", seq++);
            item.put("store_id", storeId);
            item.put("target_arrival", "07:00-09:00");
            item.put("urgency", "normal");
            schedule.add(item);
        }

        Map<String, Object> plan = new HashMap<>();
        plan.put("driver_id", driverId);
        plan.put("total_stores", storeIds.size());
        plan.put("schedule", schedule);
        plan.put("tips", List.of("AI服务不可用，使用默认排序"));
        return plan;
    }
}
