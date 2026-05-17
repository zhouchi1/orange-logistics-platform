package com.orange.logistics.model.entity;

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
 * 物流轨迹事件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tracking_event")
public class TrackingEvent {

    @Id
    private Long id;

    @Column("waybill_no")
    private String waybillNo;

    @Column("order_id")
    private String orderId;

    @Column("status")
    private LogisticsStatus status;

    @Column("station_code")
    private String stationCode;

    @Column("station_name")
    private String stationName;

    @Column("city")
    private String city;

    @Column("province")
    private String province;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("operator")
    private String operator;

    @Column("remark")
    private String remark;

    @Column("event_time")
    private LocalDateTime eventTime;

    @Column("create_time")
    private LocalDateTime createTime;
}
