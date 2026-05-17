package com.orange.logistics.scheduler.repository;

import com.orange.logistics.scheduler.entity.TaskLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TaskLogRepository extends ReactiveCrudRepository<TaskLog, Long> {

    Flux<TaskLog> findByJobName(String jobName);

    Flux<TaskLog> findByStatus(String status);

    Flux<TaskLog> findTop10ByOrderByStartTimeDesc();
}
