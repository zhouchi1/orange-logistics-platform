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
 * 超时关单任务
 * 扫描超过72h未揽收的订单自动关闭
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final DatabaseClient databaseClient;
    private final JobService jobService;

    @XxlJob("orderTimeoutHandler")
    public void execute() {
        XxlJobHelper.log("【超时关单任务】开始执行...");
        log.info("【超时关单任务】开始执行...");

        jobService.logJobStart("OrderTimeoutJob")
                .flatMap(taskLog -> {
                    LocalDateTime threshold = LocalDateTime.now().minusHours(72);

                    return databaseClient.sql(
                            "UPDATE orders SET status = 'CLOSED', close_reason = 'AUTO_TIMEOUT', " +
                            "updated_at = NOW() WHERE status = 'PENDING_PICKUP' AND created_at < :threshold")
                            .bind("threshold", threshold)
                            .fetch()
                            .rowsUpdated()
                            .flatMap(count -> {
                                log.info("【超时关单任务】关闭{}个超时订单", count);
                                XxlJobHelper.log("关闭{}个超时订单", count);
                                return jobService.logJobSuccess(
                                        taskLog.getId(), count.intValue(),
                                        "关闭" + count + "个超72h未揽收的订单");
                            })
                            .onErrorResume(e -> {
                                log.error("【超时关单任务】执行失败", e);
                                XxlJobHelper.handleFail("执行失败: " + e.getMessage());
                                return jobService.logJobFailed(taskLog.getId(), e.getMessage());
                            });
                })
                .block();
    }
}
