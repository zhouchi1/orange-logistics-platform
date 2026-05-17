package com.orange.logistics.risk.service;

import com.orange.logistics.risk.dto.RiskAssessmentRequest;
import com.orange.logistics.risk.dto.RiskAssessmentResponse;
import com.orange.logistics.risk.entity.RiskEvent;
import com.orange.logistics.risk.enums.RiskLevel;
import com.orange.logistics.risk.enums.RiskType;
import com.orange.logistics.risk.repository.BlacklistRepository;
import com.orange.logistics.risk.repository.RiskEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控服务核心逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private final AddressRiskScorer addressRiskScorer;
    private final BehaviorDetector behaviorDetector;
    private final BlacklistRepository blacklistRepository;
    private final RiskEventRepository riskEventRepository;

    /**
     * 综合风险评估
     */
    public Mono<RiskAssessmentResponse> assess(RiskAssessmentRequest request) {
        return Mono.zip(
                // 1. 黑名单检查
                checkBlacklist(request),
                // 2. 地址风险评分
                addressRiskScorer.scoreAddress(
                        request.getAddress(), request.getProvince(),
                        request.getCity(), request.getDistrict()),
                // 3. 行为检测
                behaviorDetector.detectBehavior(request.getUserId())
        ).flatMap(tuple -> {
            List<RiskAssessmentResponse.RiskDetail> allDetails = new ArrayList<>();
            if (tuple.getT1() != null) allDetails.add(tuple.getT1());
            allDetails.addAll(tuple.getT2());
            allDetails.addAll(tuple.getT3());

            // 计算综合分数
            int totalScore = allDetails.stream()
                    .mapToInt(RiskAssessmentResponse.RiskDetail::getScore)
                    .sum();
            totalScore = Math.min(totalScore, 100);

            RiskLevel level = RiskLevel.fromScore(totalScore);
            boolean passed = (level == RiskLevel.LOW || level == RiskLevel.MEDIUM);

            String suggestion = switch (level) {
                case LOW -> "正常放行";
                case MEDIUM -> "建议人工复核";
                case HIGH -> "建议拦截，需人工审核后放行";
                case CRITICAL -> "强制拦截，禁止配送";
            };

            RiskAssessmentResponse response = RiskAssessmentResponse.builder()
                    .totalScore(totalScore)
                    .riskLevel(level)
                    .passed(passed)
                    .riskDetails(allDetails)
                    .suggestion(suggestion)
                    .build();

            // 记录风险事件（仅中高风险）
            if (!passed) {
                RiskEvent event = RiskEvent.builder()
                        .userId(request.getUserId())
                        .orderNo(request.getOrderNo())
                        .riskType(allDetails.isEmpty() ? RiskType.ADDRESS_FUZZY : allDetails.get(0).getRiskType())
                        .riskLevel(level)
                        .riskScore(totalScore)
                        .description(suggestion)
                        .handleStatus("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                return riskEventRepository.save(event).thenReturn(response);
            }

            return Mono.just(response);
        });
    }

    /**
     * 黑名单检查
     */
    private Mono<RiskAssessmentResponse.RiskDetail> checkBlacklist(RiskAssessmentRequest request) {
        // 检查用户ID黑名单
        return blacklistRepository.findActiveByTypeAndValue("USER", request.getUserId())
                .switchIfEmpty(
                        // 检查手机号黑名单
                        blacklistRepository.findActiveByTypeAndValue("PHONE", request.getPhone())
                )
                .map(blacklist -> RiskAssessmentResponse.RiskDetail.builder()
                        .riskType(RiskType.BLACKLIST_HIT)
                        .score(80)
                        .description("命中黑名单: " + blacklist.getReason())
                        .build())
                .switchIfEmpty(Mono.empty());
    }
}
