package com.orange.logistics.risk.controller;

import com.orange.logistics.risk.dto.RiskAssessmentRequest;
import com.orange.logistics.risk.dto.RiskAssessmentResponse;
import com.orange.logistics.risk.entity.RiskEvent;
import com.orange.logistics.risk.repository.RiskEventRepository;
import com.orange.logistics.risk.service.BehaviorDetector;
import com.orange.logistics.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 风控服务接口
 */
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;
    private final BehaviorDetector behaviorDetector;
    private final RiskEventRepository riskEventRepository;

    /**
     * 风险评估
     */
    @PostMapping("/assess")
    public Mono<RiskAssessmentResponse> assess(@RequestBody RiskAssessmentRequest request) {
        return riskService.assess(request);
    }

    /**
     * 查询用户风险事件
     */
    @GetMapping("/events/{userId}")
    public Flux<RiskEvent> getUserRiskEvents(@PathVariable String userId) {
        return riskEventRepository.findByUserId(userId);
    }

    /**
     * 记录下单行为（供订单服务调用     */
    @PostMapping("/behavior/order/{userId}")
    public Mono<Void> recordOrder(@PathVariable String userId) {
        return behaviorDetector.recordOrder(userId);
    }

    /**
     * 记录拒收行为（供签收服务调用     */
    @PostMapping("/behavior/reject/{userId}")
    public Mono<Void> recordReject(@PathVariable String userId) {
        return behaviorDetector.recordReject(userId);
    }
}
