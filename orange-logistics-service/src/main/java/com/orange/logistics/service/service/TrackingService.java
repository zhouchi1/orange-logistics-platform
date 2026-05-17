package com.orange.logistics.service.service;

import com.orange.logistics.model.entity.TrackingEvent;
import com.orange.logistics.service.repository.TrackingEventRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 物流轨迹服务
 */
@Service
public class TrackingService {

    private final TrackingEventRepository trackingRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public TrackingService(TrackingEventRepository trackingRepository,
                           ReactiveRedisTemplate<String, String> redisTemplate) {
        this.trackingRepository = trackingRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询运单轨迹
     */
    public Flux<TrackingEvent> getTrackingByWaybill(String waybillNo) {
        return trackingRepository.findByWaybillNoOrderByEventTimeDesc(waybillNo);
    }

    /**
     * 查询订单轨迹
     */
    public Flux<TrackingEvent> getTrackingByOrder(String orderId) {
        return trackingRepository.findByOrderIdOrderByEventTimeDesc(orderId);
    }

    /**
     * 查询城市最新轨     */
    public Flux<TrackingEvent> getTrackingByCity(String city, int limit) {
        return trackingRepository.findByCityLatest(city, limit);
    }

    /**
     * 获取实时位置（从 Redis     */
    public Mono<Map<Object, Object>> getRealtimeLocation(String waybillNo) {
        return redisTemplate.opsForHash().entries("pkg:location:" + waybillNo)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
