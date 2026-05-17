package com.orange.logistics.service.controller;

import com.orange.logistics.model.entity.AnomalyRecord;
import com.orange.logistics.service.service.AnomalyService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 异常告警 REST API
 */
@RestController
@RequestMapping("/api/v1/anomalies")
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    /**
     * 获取未解决的异常列表
     */
    @GetMapping("/unresolved")
    public Flux<AnomalyRecord> getUnresolvedAnomalies() {
        return anomalyService.getUnresolvedAnomalies();
    }

    /**
     * 按严重程度获取异     */
    @GetMapping("/severity/{level}")
    public Flux<AnomalyRecord> getAnomaliesBySeverity(
            @PathVariable int level,
            @RequestParam(defaultValue = "50") int limit) {
        return anomalyService.getAnomaliesBySeverity(level, limit);
    }

    /**
     * 获取运单异常记录
     */
    @GetMapping("/waybill/{waybillNo}")
    public Flux<AnomalyRecord> getAnomaliesByWaybill(@PathVariable String waybillNo) {
        return anomalyService.getAnomaliesByWaybill(waybillNo);
    }

    /**
     * 获取站点异常
     */
    @GetMapping("/station/{stationCode}")
    public Flux<AnomalyRecord> getAnomaliesByStation(@PathVariable String stationCode) {
        return anomalyService.getAnomaliesByStation(stationCode);
    }

    /**
     * 标记异常为已解决
     */
    @PutMapping("/{id}/resolve")
    public Mono<AnomalyRecord> resolveAnomaly(@PathVariable Long id) {
        return anomalyService.resolveAnomaly(id);
    }

    /**
     * 获取异常统计概览
     */
    @GetMapping("/overview")
    public Mono<Map<String, Object>> getOverview() {
        return anomalyService.getAnomalyOverview();
    }

    /**
     * 获取包裹实时状     */
    @GetMapping("/realtime/{waybillNo}")
    public Mono<Map<Object, Object>> getRealtimeStatus(@PathVariable String waybillNo) {
        return anomalyService.getPackageRealtimeStatus(waybillNo);
    }
}
