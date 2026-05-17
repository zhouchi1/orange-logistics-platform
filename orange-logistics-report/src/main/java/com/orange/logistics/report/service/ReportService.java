package com.orange.logistics.report.service;

import com.orange.logistics.report.dto.KpiResponse;
import com.orange.logistics.report.dto.RegionStatistics;
import com.orange.logistics.report.dto.TrendData;
import com.orange.logistics.report.entity.DailyReport;
import com.orange.logistics.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final KpiCalculator kpiCalculator;
    private final TrendAnalyzer trendAnalyzer;

    /**
     * 获取指定日期范围KPI
     */
    public Mono<KpiResponse> getKpi(LocalDate startDate, LocalDate endDate) {
        return kpiCalculator.calculateKpi(startDate, endDate);
    }

    /**
     * 获取今日 KPI
     */
    public Mono<KpiResponse> getTodayKpi() {
        return kpiCalculator.calculateTodayKpi();
    }

    /**
     * 按省份聚合统     */
    public Flux<RegionStatistics> getRegionStatistics(LocalDate startDate, LocalDate endDate) {
        return reportRepository.findByReportDateBetween(startDate, endDate)
                .collectList()
                .flatMapMany(reports -> {
                    Map<String, List<DailyReport>> grouped = reports.stream()
                            .collect(Collectors.groupingBy(DailyReport::getProvince));

                    List<RegionStatistics> stats = grouped.entrySet().stream()
                            .map(entry -> {
                                String province = entry.getKey();
                                List<DailyReport> provinceReports = entry.getValue();
                                long total = provinceReports.stream().mapToLong(DailyReport::getTotalOrders).sum();
                                long signed = provinceReports.stream().mapToLong(DailyReport::getSignedOrders).sum();
                                long onTime = provinceReports.stream().mapToLong(DailyReport::getOnTimeOrders).sum();

                                BigDecimal deliveryRate = total > 0
                                        ? BigDecimal.valueOf(signed * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                                BigDecimal onTimeRate = signed > 0
                                        ? BigDecimal.valueOf(onTime * 100.0 / signed).setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                                BigDecimal avgHours = provinceReports.stream()
                                        .map(DailyReport::getAvgDeliveryHours)
                                        .filter(h -> h != null)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                long validCount = provinceReports.stream()
                                        .filter(r -> r.getAvgDeliveryHours() != null).count();

                                return RegionStatistics.builder()
                                        .province(province)
                                        .totalOrders(total)
                                        .signedOrders(signed)
                                        .deliveryRate(deliveryRate)
                                        .onTimeRate(onTimeRate)
                                        .avgDeliveryHours(validCount > 0
                                                ? avgHours.divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return Flux.fromIterable(stats);
                });
    }

    /**
     * 按城市聚合统     */
    public Flux<RegionStatistics> getCityStatistics(String province, LocalDate startDate, LocalDate endDate) {
        return reportRepository.findByReportDateBetweenAndProvince(startDate, endDate, province)
                .collectList()
                .flatMapMany(reports -> {
                    Map<String, List<DailyReport>> grouped = reports.stream()
                            .collect(Collectors.groupingBy(DailyReport::getCity));

                    List<RegionStatistics> stats = grouped.entrySet().stream()
                            .map(entry -> {
                                long total = entry.getValue().stream().mapToLong(DailyReport::getTotalOrders).sum();
                                long signed = entry.getValue().stream().mapToLong(DailyReport::getSignedOrders).sum();
                                long onTime = entry.getValue().stream().mapToLong(DailyReport::getOnTimeOrders).sum();

                                return RegionStatistics.builder()
                                        .province(province)
                                        .city(entry.getKey())
                                        .totalOrders(total)
                                        .signedOrders(signed)
                                        .deliveryRate(total > 0
                                                ? BigDecimal.valueOf(signed * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .onTimeRate(signed > 0
                                                ? BigDecimal.valueOf(onTime * 100.0 / signed).setScale(2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return Flux.fromIterable(stats);
                });
    }

    /**
     * 日趋     */
    public Mono<TrendData> getDailyTrend(LocalDate startDate, LocalDate endDate) {
        return trendAnalyzer.analyzeDailyTrend(startDate, endDate);
    }

    /**
     * 周趋     */
    public Mono<TrendData> getWeeklyTrend(int weeks) {
        return trendAnalyzer.analyzeWeeklyTrend(weeks);
    }

    /**
     * 月趋     */
    public Mono<TrendData> getMonthlyTrend(int months) {
        return trendAnalyzer.analyzeMonthlyTrend(months);
    }
}
