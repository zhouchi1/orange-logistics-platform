package com.orange.logistics.report.service;

import com.orange.logistics.report.dto.TrendData;
import com.orange.logistics.report.entity.DailyReport;
import com.orange.logistics.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 趋势分析器
 * - 按天/周/月聚合
 * - 计算环比、同比
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendAnalyzer {

    private final ReportRepository reportRepository;

    /**
     * 日趋势分析
     */
    public Mono<TrendData> analyzeDailyTrend(LocalDate startDate, LocalDate endDate) {
        return reportRepository.findByReportDateBetween(startDate, endDate)
                .collectList()
                .map(reports -> {
                    // 按日期聚合
                    Map<LocalDate, List<DailyReport>> grouped = reports.stream()
                            .collect(Collectors.groupingBy(DailyReport::getReportDate));

                    List<TrendData.TrendPoint> points = grouped.entrySet().stream()
                            .sorted(Comparator.comparing(Map.Entry::getKey))
                            .map(entry -> {
                                LocalDate date = entry.getKey();
                                List<DailyReport> dayReports = entry.getValue();
                                long total = dayReports.stream().mapToLong(DailyReport::getTotalOrders).sum();
                                long signed = dayReports.stream().mapToLong(DailyReport::getSignedOrders).sum();
                                long onTime = dayReports.stream().mapToLong(DailyReport::getOnTimeOrders).sum();

                                BigDecimal deliveryRate = total > 0
                                        ? BigDecimal.valueOf(signed * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                                BigDecimal onTimeRate = signed > 0
                                        ? BigDecimal.valueOf(onTime * 100.0 / signed).setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;

                                return TrendData.TrendPoint.builder()
                                        .date(date)
                                        .totalOrders(total)
                                        .deliveryRate(deliveryRate)
                                        .onTimeRate(onTimeRate)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    // 计算环比（最后一天 vs 倒数第二天）
                    BigDecimal chainRate = calculateChainRate(points);

                    return TrendData.builder()
                            .trendType("DAILY")
                            .points(points)
                            .chainRate(chainRate)
                            .yearOnYearRate(BigDecimal.ZERO) // 同比需要去年数据
                            .build();
                });
    }

    /**
     * 周趋势分析
     */
    public Mono<TrendData> analyzeWeeklyTrend(int weeks) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusWeeks(weeks);
        return reportRepository.findByReportDateBetween(startDate, endDate)
                .collectList()
                .map(reports -> {
                    // 按周聚合（以周一为起始）
                    Map<LocalDate, List<DailyReport>> grouped = reports.stream()
                            .collect(Collectors.groupingBy(r -> r.getReportDate()
                                    .minusDays(r.getReportDate().getDayOfWeek().getValue() - 1)));

                    List<TrendData.TrendPoint> points = grouped.entrySet().stream()
                            .sorted(Comparator.comparing(Map.Entry::getKey))
                            .map(entry -> {
                                long total = entry.getValue().stream().mapToLong(DailyReport::getTotalOrders).sum();
                                long signed = entry.getValue().stream().mapToLong(DailyReport::getSignedOrders).sum();
                                long onTime = entry.getValue().stream().mapToLong(DailyReport::getOnTimeOrders).sum();

                                return TrendData.TrendPoint.builder()
                                        .date(entry.getKey())
                                        .totalOrders(total)
                                        .deliveryRate(total > 0
                                                ? BigDecimal.valueOf(signed * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .onTimeRate(signed > 0
                                                ? BigDecimal.valueOf(onTime * 100.0 / signed).setScale(2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return TrendData.builder()
                            .trendType("WEEKLY")
                            .points(points)
                            .chainRate(calculateChainRate(points))
                            .yearOnYearRate(BigDecimal.ZERO)
                            .build();
                });
    }

    /**
     * 月趋势分析
     */
    public Mono<TrendData> analyzeMonthlyTrend(int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        return reportRepository.findByReportDateBetween(startDate, endDate)
                .collectList()
                .map(reports -> {
                    // 按月聚合
                    Map<LocalDate, List<DailyReport>> grouped = reports.stream()
                            .collect(Collectors.groupingBy(r -> r.getReportDate().withDayOfMonth(1)));

                    List<TrendData.TrendPoint> points = grouped.entrySet().stream()
                            .sorted(Comparator.comparing(Map.Entry::getKey))
                            .map(entry -> {
                                long total = entry.getValue().stream().mapToLong(DailyReport::getTotalOrders).sum();
                                long signed = entry.getValue().stream().mapToLong(DailyReport::getSignedOrders).sum();
                                long onTime = entry.getValue().stream().mapToLong(DailyReport::getOnTimeOrders).sum();

                                return TrendData.TrendPoint.builder()
                                        .date(entry.getKey())
                                        .totalOrders(total)
                                        .deliveryRate(total > 0
                                                ? BigDecimal.valueOf(signed * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .onTimeRate(signed > 0
                                                ? BigDecimal.valueOf(onTime * 100.0 / signed).setScale(2, RoundingMode.HALF_UP)
                                                : BigDecimal.ZERO)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return TrendData.builder()
                            .trendType("MONTHLY")
                            .points(points)
                            .chainRate(calculateChainRate(points))
                            .yearOnYearRate(BigDecimal.ZERO)
                            .build();
                });
    }

    /**
     * 计算环比增长率
     */
    private BigDecimal calculateChainRate(List<TrendData.TrendPoint> points) {
        if (points.size() < 2) {
            return BigDecimal.ZERO;
        }
        TrendData.TrendPoint current = points.get(points.size() - 1);
        TrendData.TrendPoint previous = points.get(points.size() - 2);
        if (previous.getTotalOrders() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(current.getTotalOrders() - previous.getTotalOrders())
                .divide(BigDecimal.valueOf(previous.getTotalOrders()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
