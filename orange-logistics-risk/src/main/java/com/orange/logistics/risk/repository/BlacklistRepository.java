package com.orange.logistics.risk.repository;

import com.orange.logistics.risk.entity.Blacklist;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BlacklistRepository extends ReactiveCrudRepository<Blacklist, Long> {

    Flux<Blacklist> findByTypeAndEnabledTrue(String type);

    @Query("SELECT * FROM blacklist WHERE type = :type AND value = :value AND enabled = true " +
           "AND (expire_at IS NULL OR expire_at > NOW())")
    Mono<Blacklist> findActiveByTypeAndValue(String type, String value);

    Flux<Blacklist> findByEnabledTrue();
}
