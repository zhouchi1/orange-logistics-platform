package com.orange.logistics.scheduler.job;

import com.orange.logistics.scheduler.service.JobService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据清理任务
 * 删除90天前的日志数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupJob {

    private final DatabaseClient databaseClient;
    private final JobService jobService;

    @XxlJob("dataCleanupHandler")
    public void execute() {
        XxlJobHelper.log("【数据清理任务】开始执行...");
        log.info("【数据清理任务】开始执行...");

        jobService.logJobStart("DataCleanupJob")
                .flatMap(taskLog -> {
                    LocalDateTime threshold = LocalDateTime.now().minusDays(90);

                    return databaseClient.sql(
                            "DELETE FROM task_log WHERE start_time < :threshold")
                            .bind("threshold", threshold)
                            .fetch()
                            .rowsUpdated()
                            .flatMap(taskLogCount -> {
                                return databaseClient.sql(
                                        "DELETE FROM notification_record WHERE created_at < :threshold")
                                        .bind("threshold", threshold)
                                        .fetch()
                                        .rowsUpdated()
                                        .map(notifCount -> taskLogCount + notifCount);
                            })
                            .flatMap(totalCount -> {
                                log.info("【数据清理任务】清理{}条过期数据", totalCount);
                                XxlJobHelper.log("清理{}条过期数据", totalCount);
                                return jobService.logJobSuccess(
                                        taskLog.getId(), totalCount.intValue(),
                                        "清理" + totalCount + "条90天前的过期数据");
                            })
                            .onErrorResume(e -> {
                                log.error("【数据清理任务】执行失败", e);
                                XxlJobHelper.handleFail("执行失败: " + e.getMessage());
                                return jobService.logJobFailed(taskLog.getId(), e.getMessage());
                            });
                })
                .block();
    }
}
