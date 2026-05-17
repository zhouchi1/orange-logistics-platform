package com.orange.logistics.notification.repository;

import com.orange.logistics.notification.entity.NotificationRecord;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RecordRepository extends ReactiveCrudRepository<NotificationRecord, Long> {

    Flux<NotificationRecord> findByUserId(String userId);

    Flux<NotificationRecord> findByUserIdAndEventType(String userId, String eventType);
}
