package com.orange.logistics.dispatch.controller;

import com.orange.logistics.dispatch.service.ArrivalOptimizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 到货时间优化 Controller
 * 提供门店最优到货时间分析和司机配送计划接口
 */
@RestController
@RequestMapping("/api/dispatch/arrival")
@RequiredArgsConstructor
public class ArrivalOptimizationController {

    private final ArrivalOptimizationService arrivalOptimizationService;

    /**
     * 查询单个门店最优到货时间
     * GET /api/dispatch/arrival/optimal?storeId=STORE_001&inventoryRatio=0.3
     */
    @GetMapping("/optimal")
    public Map<String, Object> getOptimalArrival(
            @RequestParam String storeId,
            @RequestParam(defaultValue = "0.5") double inventoryRatio) {
        return arrivalOptimizationService.getOptimalArrivalTime(storeId, inventoryRatio);
    }

    /**
     * 批量分析门店最优到货时间
     * POST /api/dispatch/arrival/batch
     */
    @PostMapping("/batch")
    public Map<String, Object> batchAnalyze(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> storeIds = (List<String>) request.get("storeIds");
        @SuppressWarnings("unchecked")
        Map<String, Double> inventoryData = (Map<String, Double>) request.get("inventoryData");
        return arrivalOptimizationService.batchAnalyze(storeIds, inventoryData);
    }

    /**
     * 生成司机配送计划
     * POST /api/dispatch/arrival/plan
     */
    @PostMapping("/plan")
    public Map<String, Object> generatePlan(@RequestBody Map<String, Object> request) {
        String driverId = (String) request.get("driverId");
        @SuppressWarnings("unchecked")
        List<String> storeIds = (List<String>) request.get("storeIds");
        @SuppressWarnings("unchecked")
        Map<String, Double> inventoryData = (Map<String, Double>) request.get("inventoryData");
        return arrivalOptimizationService.generateDeliveryPlan(driverId, storeIds, inventoryData);
    }

    /**
     * 获取门店销售曲线
     * GET /api/dispatch/arrival/curve/{storeId}
     */
    @GetMapping("/curve/{storeId}")
    public Map<String, Object> getRevenueCurve(
            @PathVariable String storeId,
            @RequestParam(required = false) Integer dayOfWeek) {
        return arrivalOptimizationService.getStoreRevenueCurve(storeId, dayOfWeek);
    }
}
