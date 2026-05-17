package com.orange.logistics.service.controller;

import com.orange.logistics.model.entity.TrackingEvent;
import com.orange.logistics.service.service.TrackingService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 物流轨迹 REST API
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /**
     * 查询运单轨迹
     */
    @GetMapping("/waybill/{waybillNo}")
    public Flux<TrackingEvent> getTrackingByWaybill(@PathVariable String waybillNo) {
        return trackingService.getTrackingByWaybill(waybillNo);
    }

    /**
     * 查询订单轨迹
     */
    @GetMapping("/order/{orderId}")
    public Flux<TrackingEvent> getTrackingByOrder(@PathVariable String orderId) {
        return trackingService.getTrackingByOrder(orderId);
    }

    /**
     * 查询城市最新轨     */
    @GetMapping("/city/{city}")
    public Flux<TrackingEvent> getTrackingByCity(
            @PathVariable String city,
            @RequestParam(defaultValue = "100") int limit) {
        return trackingService.getTrackingByCity(city, limit);
    }

    /**
     * 获取实时位置
     */
    @GetMapping("/location/{waybillNo}")
    public Mono<Map<Object, Object>> getRealtimeLocation(@PathVariable String waybillNo) {
        return trackingService.getRealtimeLocation(waybillNo);
    }
}
