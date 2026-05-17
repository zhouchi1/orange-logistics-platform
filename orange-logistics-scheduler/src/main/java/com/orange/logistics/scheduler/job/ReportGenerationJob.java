package com.orange.logistics.scheduler.job;

import com.orange.logistics.scheduler.service.JobService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表生成任务
 * 每日凌晨生成前一天报表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationJob {

    private final DatabaseClient databaseClient;
    private final JobService jobService;

    @XxlJob("reportGenerationHandler")
    public void execute() {
        XxlJobHelper.log("【报表生成任务】开始执行...");
        log.info("【报表生成任务】开始执行...");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = yesterday.atTime(23, 59, 59);

        jobService.logJobStart("ReportGenerationJob")
                .flatMap(taskLog -> {
                    String sql = """
                        INSERT INTO daily_report (report_date, province, city, total_orders, signed_orders,
                            on_time_orders, delivery_rate, on_time_rate, avg_delivery_hours, exception_orders, created_at)
                        SELECT
                            :reportDate as report_date,
                            COALESCE(sender_province, 'unknown') as province,
                            COALESCE(sender_city, 'unknown') as city,
                            COUNT(*) as total_orders,
                            SUM(CASE WHEN status = 'SIGNED' THEN 1 ELSE 0 END) as signed_orders,
                            SUM(CASE WHEN status = 'SIGNED' AND TIMESTAMPDIFF(HOUR, created_at, updated_at) <= 48 THEN 1 ELSE 0 END) as on_time_orders,
                            ROUND(SUM(CASE WHEN status = 'SIGNED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as delivery_rate,
                            ROUND(
                                CASE WHEN SUM(CASE WHEN status = 'SIGNED' THEN 1 ELSE 0 END) > 0
                                THEN SUM(CASE WHEN status = 'SIGNED' AND TIMESTAMPDIFF(HOUR, created_at, updated_at) <= 48 THEN 1 ELSE 0 END) * 100.0
                                    / SUM(CASE WHEN status = 'SIGNED' THEN 1 ELSE 0 END)
                                ELSE 0 END, 2) as on_time_rate,
                            ROUND(AVG(CASE WHEN status = 'SIGNED' THEN TIMESTAMPDIFF(HOUR, created_at, updated_at) ELSE NULL END), 2) as avg_delivery_hours,
                            SUM(CASE WHEN status = 'EXCEPTION' THEN 1 ELSE 0 END) as exception_orders,
                            NOW() as created_at
                        FROM orders
                        WHERE created_at BETWEEN :startOfDay AND :endOfDay
                        GROUP BY sender_province, sender_city
                        """;

                    return databaseClient.sql(sql)
                            .bind("reportDate", yesterday)
                            .bind("startOfDay", startOfDay)
                            .bind("endOfDay", endOfDay)
                            .fetch()
                            .rowsUpdated()
                            .flatMap(count -> {
                                log.info("【报表生成任务】生成{}条区域报表记录，日期: {}", count, yesterday);
                                XxlJobHelper.log("生成{}日报表，共{}条区域记录", yesterday, count);
                                return jobService.logJobSuccess(
                                        taskLog.getId(), count.intValue(),
                                        "生成" + yesterday + "日报表，共" + count + "条区域记录");
                            })
                            .onErrorResume(e -> {
                                log.error("【报表生成任务】执行失败", e);
                                XxlJobHelper.handleFail("执行失败: " + e.getMessage());
                                return jobService.logJobFailed(taskLog.getId(), e.getMessage());
                            });
                })
                .block();
    }
}
