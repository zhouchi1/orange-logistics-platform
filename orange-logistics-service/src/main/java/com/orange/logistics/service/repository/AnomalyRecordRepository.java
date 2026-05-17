package com.orange.logistics.service.repository;

import com.orange.logistics.model.entity.AnomalyRecord;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 异常记录 Repository
 */
@Repository
public interface AnomalyRecordRepository extends ReactiveCrudRepository<AnomalyRecord, Long> {

    Flux<AnomalyRecord> findByWaybillNo(String waybillNo);

    Flux<AnomalyRecord> findByResolvedFalseOrderByDetectedTimeDesc();

    @Query("SELECT * FROM anomaly_record WHERE resolved = false AND severity >= :severity ORDER BY detected_time DESC LIMIT :limit")
    Flux<AnomalyRecord> findUnresolvedBySeverity(int severity, int limit);

    @Query("SELECT COUNT(*) FROM anomaly_record WHERE resolved = false")
    Mono<Long> countUnresolved();

    @Query("SELECT * FROM anomaly_record WHERE station_code = :stationCode AND resolved = false")
    Flux<AnomalyRecord> findUnresolvedByStation(String stationCode);
}
