package com.orange.logistics.report.repository;

import com.orange.logistics.report.entity.DailyReport;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ReportRepository extends ReactiveCrudRepository<DailyReport, Long> {

    Flux<DailyReport> findByReportDateBetween(LocalDate startDate, LocalDate endDate);

    Flux<DailyReport> findByProvince(String province);

    Flux<DailyReport> findByProvinceAndCity(String province, String city);

    @Query("SELECT * FROM daily_report WHERE report_date = :reportDate")
    Flux<DailyReport> findByReportDate(LocalDate reportDate);

    Flux<DailyReport> findByReportDateBetweenAndProvince(LocalDate startDate, LocalDate endDate, String province);
}
