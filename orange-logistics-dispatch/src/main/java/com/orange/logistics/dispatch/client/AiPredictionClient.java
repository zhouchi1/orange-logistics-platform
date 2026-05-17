package com.orange.logistics.dispatch.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 预测服务 Feign Client
 * 调用 ai-prediction 服务获取门店最优到货时间
 */
@FeignClient(
    name = "ai-prediction-client",
    url = "${ai.prediction.url:http://localhost:8001}"
)
public interface AiPredictionClient {

    /**
     * 分析单个门店最优到货时间
     */
    @PostMapping("/api/v1/arrival/analyze/single")
    Map<String, Object> analyzeSingleStore(@RequestBody Map<String, Object> request);

    /**
     * 批量分析门店最优到货时间
     */
    @PostMapping("/api/v1/arrival/analyze/batch")
    Map<String, Object> analyzeBatch(@RequestBody Map<String, Object> request);

    /**
     * 生成司机配送排班建议
     */
    @PostMapping("/api/v1/arrival/schedule/driver")
    Map<String, Object> generateDriverSchedule(@RequestBody Map<String, Object> request);

    /**
     * 获取门店销售曲线
     */
    @GetMapping("/api/v1/arrival/revenue-curve/{storeId}")
    Map<String, Object> getRevenueCurve(
        @PathVariable("storeId") String storeId,
        @RequestParam(value = "day_of_week", required = false) Integer dayOfWeek
    );

    /**
     * 获取门店到货历史
     */
    @GetMapping("/api/v1/arrival/history/{storeId}")
    Map<String, Object> getArrivalHistory(
        @PathVariable("storeId") String storeId,
        @RequestParam(value = "days", defaultValue = "90") int days
    );
}
