package com.orange.logistics.service.repository;

import com.orange.logistics.model.entity.TrackingEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * 物流轨迹 Repository
 */
@Repository
public interface TrackingEventRepository extends ReactiveCrudRepository<TrackingEvent, Long> {

    Flux<TrackingEvent> findByWaybillNoOrderByEventTimeDesc(String waybillNo);

    Flux<TrackingEvent> findByOrderIdOrderByEventTimeDesc(String orderId);

    @Query("SELECT * FROM tracking_event WHERE city = :city ORDER BY event_time DESC LIMIT :limit")
    Flux<TrackingEvent> findByCityLatest(String city, int limit);
}
