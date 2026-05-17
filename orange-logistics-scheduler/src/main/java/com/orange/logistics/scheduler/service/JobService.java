package com.orange.logistics.scheduler.service;

import com.orange.logistics.scheduler.entity.TaskLog;
import com.orange.logistics.scheduler.repository.TaskLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final TaskLogRepository taskLogRepository;

    /**
     * 记录任务开     */
    public Mono<TaskLog> logJobStart(String jobName) {
        TaskLog taskLog = TaskLog.builder()
                .jobName(jobName)
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .build();
        return taskLogRepository.save(taskLog);
    }

    /**
     * 记录任务成功
     */
    public Mono<TaskLog> logJobSuccess(Long logId, int processedCount, String message) {
        return taskLogRepository.findById(logId)
                .flatMap(taskLog -> {
                    taskLog.setStatus("SUCCESS");
                    taskLog.setEndTime(LocalDateTime.now());
                    taskLog.setDuration(java.time.Duration.between(
                            taskLog.getStartTime(), taskLog.getEndTime()).toMillis());
                    taskLog.setProcessedCount(processedCount);
                    taskLog.setResultMessage(message);
                    return taskLogRepository.save(taskLog);
                });
    }

    /**
     * 记录任务失败
     */
    public Mono<TaskLog> logJobFailed(Long logId, String errorMessage) {
        return taskLogRepository.findById(logId)
                .flatMap(taskLog -> {
                    taskLog.setStatus("FAILED");
                    taskLog.setEndTime(LocalDateTime.now());
                    taskLog.setDuration(java.time.Duration.between(
                            taskLog.getStartTime(), taskLog.getEndTime()).toMillis());
                    taskLog.setErrorMessage(errorMessage);
                    return taskLogRepository.save(taskLog);
                });
    }

    /**
     * 查询最近任务日     */
    public Flux<TaskLog> getRecentLogs() {
        return taskLogRepository.findTop10ByOrderByStartTimeDesc();
    }

    /**
     * 按任务名查询日志
     */
    public Flux<TaskLog> getLogsByJobName(String jobName) {
        return taskLogRepository.findByJobName(jobName);
    }
}
