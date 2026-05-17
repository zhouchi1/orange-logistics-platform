package com.orange.logistics.risk.repository;

import com.orange.logistics.risk.entity.RiskEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RiskEventRepository extends ReactiveCrudRepository<RiskEvent, Long> {

    Flux<RiskEvent> findByUserId(String userId);

    Flux<RiskEvent> findByOrderNo(String orderNo);

    Flux<RiskEvent> findByUserIdAndHandleStatus(String userId, String handleStatus);
}
