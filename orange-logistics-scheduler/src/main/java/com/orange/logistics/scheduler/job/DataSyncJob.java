package com.orange.logistics.scheduler.job;

import com.orange.logistics.scheduler.service.JobService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 数据同步任务
 * 将热点数据同步到 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncJob {

    private final DatabaseClient databaseClient;
    private final JobService jobService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @XxlJob("dataSyncHandler")
    public void execute() {
        XxlJobHelper.log("【数据同步任务】开始执行...");
        log.info("【数据同步任务】开始执行...");

        jobService.logJobStart("DataSyncJob")
                .flatMap(taskLog -> {
                    return syncTodayOrderStats()
                            .then(syncActiveVehicleCount())
                            .then(Mono.just(2))
                            .flatMap(count -> {
                                log.info("【数据同步任务】同步{}项统计数据到Redis", count);
                                XxlJobHelper.log("同步{}项统计数据到Redis", count);
                                return jobService.logJobSuccess(
                                        taskLog.getId(), count,
                                        "同步" + count + "项统计数据到Redis缓存");
                            })
                            .onErrorResume(e -> {
                                log.error("【数据同步任务】执行失败", e);
                                XxlJobHelper.handleFail("执行失败: " + e.getMessage());
                                return jobService.logJobFailed(taskLog.getId(), e.getMessage());
                            });
                })
                .block();
    }

    private Mono<Void> syncTodayOrderStats() {
        return databaseClient.sql(
                "SELECT COUNT(*) as cnt FROM orders WHERE DATE(created_at) = CURDATE()")
                .fetch()
                .one()
                .flatMap(row -> {
                    String count = String.valueOf(row.get("cnt"));
                    return redisTemplate.opsForValue()
                            .set("stats:today:orders", count, Duration.ofMinutes(35))
                            .then();
                });
    }

    private Mono<Void> syncActiveVehicleCount() {
        return databaseClient.sql(
                "SELECT COUNT(*) as cnt FROM vehicles WHERE status = 'ACTIVE'")
                .fetch()
                .one()
                .flatMap(row -> {
                    String count = String.valueOf(row.get("cnt"));
                    return redisTemplate.opsForValue()
                            .set("stats:active:vehicles", count, Duration.ofMinutes(35))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("同步车辆数据失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
