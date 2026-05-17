package com.orange.logistics.risk.service;

import com.orange.logistics.risk.dto.RiskAssessmentResponse;
import com.orange.logistics.risk.enums.RiskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 行为检测器
 * - 短时间大量下单（>10单/小时）
 * - 频繁拒收（>3次/周）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorDetector {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final int MAX_ORDERS_PER_HOUR = 10;
    private static final int MAX_REJECTS_PER_WEEK = 3;

    /**
     * 检测用户行为风险
     */
    public Mono<List<RiskAssessmentResponse.RiskDetail>> detectBehavior(String userId) {
        return Mono.zip(
                checkFrequentOrders(userId),
                checkFrequentRejects(userId)
        ).map(tuple -> {
            List<RiskAssessmentResponse.RiskDetail> details = new ArrayList<>();
            if (tuple.getT1() != null) details.add(tuple.getT1());
            if (tuple.getT2() != null) details.add(tuple.getT2());
            return details;
        });
    }

    /**
     * 记录下单行为
     */
    public Mono<Void> recordOrder(String userId) {
        String key = "risk:order:count:" + userId;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, Duration.ofHours(1)).then();
                    }
                    return Mono.empty();
                });
    }

    /**
     * 记录拒收行为
     */
    public Mono<Void> recordReject(String userId) {
        String key = "risk:reject:count:" + userId;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, Duration.ofDays(7)).then();
                    }
                    return Mono.empty();
                });
    }

    /**
     * 检测短时间大量下单
     */
    private Mono<RiskAssessmentResponse.RiskDetail> checkFrequentOrders(String userId) {
        String key = "risk:order:count:" + userId;
        return redisTemplate.opsForValue().get(key)
                .map(Integer::parseInt)
                .defaultIfEmpty(0)
                .map(count -> {
                    if (count > MAX_ORDERS_PER_HOUR) {
                        return RiskAssessmentResponse.RiskDetail.builder()
                                .riskType(RiskType.FREQUENT_ORDER)
                                .score(35)
                                .description("1小时内下单" + count + "次，超过阈值" + MAX_ORDERS_PER_HOUR)
                                .build();
                    }
                    return null;
                });
    }

    /**
     * 检测频繁拒收
     */
    private Mono<RiskAssessmentResponse.RiskDetail> checkFrequentRejects(String userId) {
        String key = "risk:reject:count:" + userId;
        return redisTemplate.opsForValue().get(key)
                .map(Integer::parseInt)
                .defaultIfEmpty(0)
                .map(count -> {
                    if (count > MAX_REJECTS_PER_WEEK) {
                        return RiskAssessmentResponse.RiskDetail.builder()
                                .riskType(RiskType.FREQUENT_REJECT)
                                .score(30)
                                .description("本周拒收" + count + "次，超过阈值" + MAX_REJECTS_PER_WEEK)
                                .build();
                    }
                    return null;
                });
    }
}
