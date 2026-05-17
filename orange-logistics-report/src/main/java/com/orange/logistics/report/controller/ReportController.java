package com.orange.logistics.report.controller;

import com.orange.logistics.report.dto.KpiResponse;
import com.orange.logistics.report.dto.RegionStatistics;
import com.orange.logistics.report.dto.TrendData;
import com.orange.logistics.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 报表服务接口
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 获取 KPI 指标
     */
    @GetMapping("/kpi")
    public Mono<KpiResponse> getKpi(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getKpi(startDate, endDate);
    }

    /**
     * 获取今日 KPI
     */
    @GetMapping("/kpi/today")
    public Mono<KpiResponse> getTodayKpi() {
        return reportService.getTodayKpi();
    }

    /**
     * 按省份统     */
    @GetMapping("/region/province")
    public Flux<RegionStatistics> getProvinceStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getRegionStatistics(startDate, endDate);
    }

    /**
     * 按城市统     */
    @GetMapping("/region/city")
    public Flux<RegionStatistics> getCityStatistics(
            @RequestParam String province,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getCityStatistics(province, startDate, endDate);
    }

    /**
     * 日趋势分析     */
    @GetMapping("/trend/daily")
    public Mono<TrendData> getDailyTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getDailyTrend(startDate, endDate);
    }

    /**
     * 周趋势分析     */
    @GetMapping("/trend/weekly")
    public Mono<TrendData> getWeeklyTrend(@RequestParam(defaultValue = "4") int weeks) {
        return reportService.getWeeklyTrend(weeks);
    }

    /**
     * 月趋势分析     */
    @GetMapping("/trend/monthly")
    public Mono<TrendData> getMonthlyTrend(@RequestParam(defaultValue = "6") int months) {
        return reportService.getMonthlyTrend(months);
    }
}
