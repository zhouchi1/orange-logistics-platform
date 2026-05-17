package com.orange.logistics.model.entity;

import com.orange.logistics.model.enums.AnomalyType;
import com.orange.logistics.model.enums.LogisticsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 异常记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("anomaly_record")
public class AnomalyRecord {

    @Id
    private Long id;

    @Column("waybill_no")
    private String waybillNo;

    @Column("order_id")
    private String orderId;

    @Column("anomaly_type")
    private AnomalyType anomalyType;

    @Column("current_status")
    private LogisticsStatus currentStatus;

    @Column("station_code")
    private String stationCode;

    @Column("station_name")
    private String stationName;

    @Column("description")
    private String description;

    @Column("stagnation_minutes")
    private Long stagnationMinutes;

    @Column("severity")
    private Integer severity;

    @Column("resolved")
    private Boolean resolved;

    @Column("detected_time")
    private LocalDateTime detectedTime;

    @Column("resolved_time")
    private LocalDateTime resolvedTime;

    @Column("create_time")
    private LocalDateTime createTime;
}
