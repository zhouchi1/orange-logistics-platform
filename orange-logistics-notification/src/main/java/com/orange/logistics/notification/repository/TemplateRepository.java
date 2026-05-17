package com.orange.logistics.notification.repository;

import com.orange.logistics.notification.entity.NotificationTemplate;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface TemplateRepository extends ReactiveCrudRepository<NotificationTemplate, Long> {

    Mono<NotificationTemplate> findByTemplateCode(String templateCode);

    Mono<NotificationTemplate> findByTemplateCodeAndEnabledTrue(String templateCode);
}
