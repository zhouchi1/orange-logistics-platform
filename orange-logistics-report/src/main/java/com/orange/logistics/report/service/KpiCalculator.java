package com.orange.logistics.report.service;

import com.orange.logistics.report.dto.KpiResponse;
import com.orange.logistics.report.entity.DailyReport;
import com.orange.logistics.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * KPI 计算器
 * - 妥投率 = 已签收 / 总单量
 * - 时效达成率 = 准时签收 / 总签收
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiCalculator {

    private final ReportRepository reportRepository;

    /**
     * 计算指定日期范围KPI
     */
    public Mono<KpiResponse> calculateKpi(LocalDate startDate, LocalDate endDate) {
        return reportRepository.findByReportDateBetween(startDate, endDate)
                .collectList()
                .map(reports -> {
                    long totalOrders = reports.stream().mapToLong(DailyReport::getTotalOrders).sum();
                    long signedOrders = reports.stream().mapToLong(DailyReport::getSignedOrders).sum();
                    long onTimeOrders = reports.stream().mapToLong(DailyReport::getOnTimeOrders).sum();
                    long exceptionOrders = reports.stream().mapToLong(DailyReport::getExceptionOrders).sum();

                    // 妥投率 = 已签收 / 总单量
                    BigDecimal deliveryRate = totalOrders > 0
                            ? BigDecimal.valueOf(signedOrders)
                                .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    // 时效达成率 = 准时签收 / 总签收
                    BigDecimal onTimeRate = signedOrders > 0
                            ? BigDecimal.valueOf(onTimeOrders)
                                .divide(BigDecimal.valueOf(signedOrders), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    // 平均配送时长
                    BigDecimal avgHours = reports.stream()
                            .map(DailyReport::getAvgDeliveryHours)
                            .filter(h -> h != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long validCount = reports.stream()
                            .filter(r -> r.getAvgDeliveryHours() != null)
                            .count();
                    BigDecimal avgDeliveryHours = validCount > 0
                            ? avgHours.divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return KpiResponse.builder()
                            .deliveryRate(deliveryRate)
                            .onTimeRate(onTimeRate)
                            .totalOrders(totalOrders)
                            .signedOrders(signedOrders)
                            .onTimeOrders(onTimeOrders)
                            .exceptionOrders(exceptionOrders)
                            .avgDeliveryHours(avgDeliveryHours)
                            .period(startDate + " ~ " + endDate)
                            .build();
                });
    }

    /**
     * 计算当日 KPI
     */
    public Mono<KpiResponse> calculateTodayKpi() {
        LocalDate today = LocalDate.now();
        return calculateKpi(today, today);
    }
}
