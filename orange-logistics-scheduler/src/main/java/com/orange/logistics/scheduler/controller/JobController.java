package com.orange.logistics.scheduler.controller;

import com.orange.logistics.scheduler.entity.TaskLog;
import com.orange.logistics.scheduler.job.DataCleanupJob;
import com.orange.logistics.scheduler.job.DataSyncJob;
import com.orange.logistics.scheduler.job.OrderTimeoutJob;
import com.orange.logistics.scheduler.job.ReportGenerationJob;
import com.orange.logistics.scheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 任务管理接口
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final OrderTimeoutJob orderTimeoutJob;
    private final DataCleanupJob dataCleanupJob;
    private final ReportGenerationJob reportGenerationJob;
    private final DataSyncJob dataSyncJob;

    /**
     * 查询最近任务执行日志
     */
    @GetMapping("/logs")
    public Flux<TaskLog> getRecentLogs() {
        return jobService.getRecentLogs();
    }

    /**
     * 按任务名查询日志
     */
    @GetMapping("/logs/{jobName}")
    public Flux<TaskLog> getLogsByJobName(@PathVariable String jobName) {
        return jobService.getLogsByJobName(jobName);
    }

    /**
     * 手动触发任务
     */
    @PostMapping("/trigger/{jobName}")
    public Mono<Map<String, String>> triggerJob(@PathVariable String jobName) {
        return Mono.fromRunnable(() -> {
            switch (jobName) {
                case "OrderTimeoutJob" -> orderTimeoutJob.execute();
                case "DataCleanupJob" -> dataCleanupJob.execute();
                case "ReportGenerationJob" -> reportGenerationJob.execute();
                case "DataSyncJob" -> dataSyncJob.execute();
                default -> throw new IllegalArgumentException("未知任务: " + jobName);
            }
        }).thenReturn(Map.of("message", "任务已触发: " + jobName, "status", "TRIGGERED"));
    }

    /**
     * 获取所有可用任务列表
     */
    @GetMapping("/list")
    public Mono<Map<String, Object>> listJobs() {
        return Mono.just(Map.of(
                "jobs", java.util.List.of(
                        Map.of("name", "OrderTimeoutJob", "cron", "0 0 * * * ?", "description", "超时关单-每小时执行"),
                        Map.of("name", "DataCleanupJob", "cron", "0 0 2 * * ?", "description", "数据清理-每天凌晨2点执行"),
                        Map.of("name", "ReportGenerationJob", "cron", "0 0 1 * * ?", "description", "报表生成-每天凌晨1点执行"),
                        Map.of("name", "DataSyncJob", "cron", "0 */30 * * * ?", "description", "数据同步-每30分钟执行")
                )
        ));
    }
}
