package com.orange.logistics.risk.service;

import com.orange.logistics.risk.dto.RiskAssessmentResponse;
import com.orange.logistics.risk.enums.RiskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 地址风险评分器
 * - 模糊地址扣分
 * - 高风险区域加分
 * - 历史投诉记录
 */
@Slf4j
@Service
public class AddressRiskScorer {

    /**
     * 高风险区域列表（示例数据）
     */
    private static final Set<String> HIGH_RISK_AREAS = Set.of(
            "某某工业区", "临时安置区", "城中村", "拆迁区"
    );

    /**
     * 模糊地址关键词
     */
    private static final List<String> FUZZY_KEYWORDS = Arrays.asList(
            "附近", "旁边", "对面", "左右", "大概", "好像", "某某"
    );

    /**
     * 评估地址风险
     *
     * @param address  完整地址
     * @param province 省份
     * @param city     城市
     * @param district 区县
     * @return 风险详情列表
     */
    public Mono<List<RiskAssessmentResponse.RiskDetail>> scoreAddress(
            String address, String province, String city, String district) {

        return Mono.fromCallable(() -> {
            List<RiskAssessmentResponse.RiskDetail> details = new ArrayList<>();

            // 1. 模糊地址检查
            int fuzzyScore = checkFuzzyAddress(address);
            if (fuzzyScore > 0) {
                details.add(RiskAssessmentResponse.RiskDetail.builder()
                        .riskType(RiskType.ADDRESS_FUZZY)
                        .score(fuzzyScore)
                        .description("地址包含模糊描述，可能导致配送困难")
                        .build());
            }

            // 2. 高风险区域检查
            int areaScore = checkHighRiskArea(address);
            if (areaScore > 0) {
                details.add(RiskAssessmentResponse.RiskDetail.builder()
                        .riskType(RiskType.HIGH_RISK_AREA)
                        .score(areaScore)
                        .description("地址位于高风险区域")
                        .build());
            }

            // 3. 地址完整性检查（缺少省份扣分）
            if (province == null || province.isEmpty()) {
                details.add(RiskAssessmentResponse.RiskDetail.builder()
                        .riskType(RiskType.ADDRESS_FUZZY)
                        .score(10)
                        .description("缺少省份信息")
                        .build());
            }
            if (address != null && address.length() < 10) {
                details.add(RiskAssessmentResponse.RiskDetail.builder()
                        .riskType(RiskType.ADDRESS_FUZZY)
                        .score(15)
                        .description("地址过短，信息不完整")
                        .build());
            }

            return details;
        });
    }

    private int checkFuzzyAddress(String address) {
        if (address == null) return 20;
        int score = 0;
        for (String keyword : FUZZY_KEYWORDS) {
            if (address.contains(keyword)) {
                score += 10;
            }
        }
        return Math.min(score, 30); // 最高30分
    }

    private int checkHighRiskArea(String address) {
        if (address == null) return 0;
        for (String area : HIGH_RISK_AREAS) {
            if (address.contains(area)) {
                return 25;
            }
        }
        return 0;
    }
}
