package com.orange.logistics.service.service;

import com.orange.logistics.model.entity.AnomalyRecord;
import com.orange.logistics.service.repository.AnomalyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常记录服务
 */
@Service
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);

    private final AnomalyRecordRepository anomalyRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public AnomalyService(AnomalyRecordRepository anomalyRepository,
                          ReactiveRedisTemplate<String, String> redisTemplate) {
        this.anomalyRepository = anomalyRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取未解决的异常列表
     */
    public Flux<AnomalyRecord> getUnresolvedAnomalies() {
        return anomalyRepository.findByResolvedFalseOrderByDetectedTimeDesc();
    }

    /**
     * 按严重程度获取异     */
    public Flux<AnomalyRecord> getAnomaliesBySeverity(int severity, int limit) {
        return anomalyRepository.findUnresolvedBySeverity(severity, limit);
    }

    /**
     * 获取运单异常记录
     */
    public Flux<AnomalyRecord> getAnomaliesByWaybill(String waybillNo) {
        return anomalyRepository.findByWaybillNo(waybillNo);
    }

    /**
     * 获取站点未解决异     */
    public Flux<AnomalyRecord> getAnomaliesByStation(String stationCode) {
        return anomalyRepository.findUnresolvedByStation(stationCode);
    }

    /**
     * 标记异常为已解决
     */
    public Mono<AnomalyRecord> resolveAnomaly(Long id) {
        return anomalyRepository.findById(id)
                .flatMap(record -> {
                    record.setResolved(true);
                    record.setResolvedTime(LocalDateTime.now());
                    return anomalyRepository.save(record);
                })
                .doOnSuccess(r -> log.info("异常已解 id={}", id));
    }

    /**
     * 获取异常统计概览
     */
    public Mono<Map<String, Object>> getAnomalyOverview() {
        return anomalyRepository.countUnresolved()
                .map(count -> Map.of(
                        "unresolvedCount", (Object) count,
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    /**
     * Redis 获取实时包裹状     */
    public Mono<Map<Object, Object>> getPackageRealtimeStatus(String waybillNo) {
        return redisTemplate.opsForHash().entries("pkg:status:" + waybillNo)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
