package com.orange.logistics.transport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.transport.entity.GpsRecord;
import com.orange.logistics.transport.entity.Vehicle;
import com.orange.logistics.transport.repository.GpsRecordMapper;
import com.orange.logistics.transport.repository.VehicleMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// \ // disabled
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GPS轨迹服务（增强版）
 * - 接收GPS上报（经纬度、速度、方向）
 * - 轨迹存储（Redis GEO + MySQL）
 * - 电子围栏（进出区域告警）
 * - 里程计算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsTrackingService {

    private final GpsRecordMapper gpsRecordMapper;
    private final VehicleMapper vehicleMapper;
    private final StringRedisTemplate redisTemplate;
    // private final RocketMQTemplate rocketMQTemplate; // disabled

    private static final String GEO_KEY = "vehicle:geo:positions";
    private static final String TRACK_KEY_PREFIX = "vehicle:track:";
    private static final String FENCE_KEY_PREFIX = "geofence:";
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * 接收GPS上报并处理
     * 存储到Redis GEO + MySQL，同时检查电子围栏
     *
     * @param report GPS上报数据
     * @return 处理结果（含围栏告警信息）
     */
    @Operation(summary = "GPS数据上报")
    @Transactional
    public GpsReportResult reportGps(GpsReport report) {
        if (report.getVehicleId() == null) {
            throw new IllegalArgumentException("车辆ID不能为空");
        }
        if (report.getLongitude() == null || report.getLatitude() == null) {
            throw new IllegalArgumentException("经纬度不能为空");
        }

        // 1. 存储到MySQL
        GpsRecord record = new GpsRecord();
        record.setVehicleId(report.getVehicleId());
        record.setPlateNumber(report.getPlateNumber());
        record.setTransportOrderId(report.getTransportOrderId());
        record.setLongitude(report.getLongitude());
        record.setLatitude(report.getLatitude());
        record.setSpeed(report.getSpeed());
        record.setDirection(report.getDirection());
        record.setLocation(report.getLocation());
        record.setRecordTime(report.getRecordTime() != null ? report.getRecordTime() : LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        gpsRecordMapper.insert(record);

        // 2. 存储到Redis GEO（实时位置）
        redisTemplate.opsForGeo().add(GEO_KEY,
                new org.springframework.data.geo.Point(
                        report.getLongitude().doubleValue(),
                        report.getLatitude().doubleValue()),
                String.valueOf(report.getVehicleId()));

        // 3. 追加到轨迹列表（Redis List，保留最近1000个点）
        String trackKey = TRACK_KEY_PREFIX + report.getVehicleId() + ":"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String trackPoint = String.format("%s,%s,%s,%s",
                report.getLongitude(), report.getLatitude(),
                report.getSpeed() != null ? report.getSpeed() : "0",
                record.getRecordTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        redisTemplate.opsForList().rightPush(trackKey, trackPoint);
        redisTemplate.expire(trackKey, java.time.Duration.ofDays(7));

        // 4. 更新车辆实时位置
        Vehicle vehicle = vehicleMapper.selectById(report.getVehicleId());
        if (vehicle != null) {
            vehicle.setLongitude(report.getLongitude());
            vehicle.setLatitude(report.getLatitude());
            vehicle.setCurrentLocation(report.getLocation());
            vehicle.setUpdateTime(LocalDateTime.now());
            vehicleMapper.updateById(vehicle);
        }

        // 5. 电子围栏检查
        List<FenceAlert> alerts = checkGeofences(report.getVehicleId(), report.getLongitude(), report.getLatitude());

        GpsReportResult result = GpsReportResult.builder()
                .success(true)
                .vehicleId(report.getVehicleId())
                .fenceAlerts(alerts)
                .build();

        if (!alerts.isEmpty()) {
            log.warn("电子围栏告警: vehicleId={}, alerts={}", report.getVehicleId(), alerts.size());
        }

        return result;
    }

    /**
     * 查询车辆实时位置
     *
     * @param vehicleId 车辆ID
     * @return 最新位置信息
     */
    @Operation(summary = "查询车辆实时位置")
    public GpsPosition getLatestPosition(Long vehicleId) {
        // 优先从Redis GEO获取
        List<org.springframework.data.geo.Point> positions = redisTemplate.opsForGeo()
                .position(GEO_KEY, String.valueOf(vehicleId));

        if (positions != null && !positions.isEmpty() && positions.get(0) != null) {
            org.springframework.data.geo.Point point = positions.get(0);
            return GpsPosition.builder()
                    .vehicleId(vehicleId)
                    .longitude(BigDecimal.valueOf(point.getX()))
                    .latitude(BigDecimal.valueOf(point.getY()))
                    .source("REDIS_GEO")
                    .build();
        }

        // 回退到MySQL
        GpsRecord latest = gpsRecordMapper.selectOne(
                new LambdaQueryWrapper<GpsRecord>()
                        .eq(GpsRecord::getVehicleId, vehicleId)
                        .orderByDesc(GpsRecord::getRecordTime)
                        .last("LIMIT 1"));

        if (latest != null) {
            return GpsPosition.builder()
                    .vehicleId(vehicleId)
                    .longitude(latest.getLongitude())
                    .latitude(latest.getLatitude())
                    .speed(latest.getSpeed())
                    .direction(latest.getDirection())
                    .recordTime(latest.getRecordTime())
                    .source("MYSQL")
                    .build();
        }

        return null;
    }

    /**
     * 计算行驶里程
     * 基于GPS轨迹点计算实际行驶距离
     *
     * @param vehicleId 车辆ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 里程信息
     */
    @Operation(summary = "计算行驶里程")
    public MileageResult calculateMileage(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        if (vehicleId == null) {
            throw new IllegalArgumentException("车辆ID不能为空");
        }

        List<GpsRecord> records = gpsRecordMapper.selectList(
                new LambdaQueryWrapper<GpsRecord>()
                        .eq(GpsRecord::getVehicleId, vehicleId)
                        .ge(GpsRecord::getRecordTime, startTime)
                        .le(GpsRecord::getRecordTime, endTime)
                        .orderByAsc(GpsRecord::getRecordTime));

        if (records.size() < 2) {
            return MileageResult.builder()
                    .vehicleId(vehicleId)
                    .totalMileage(BigDecimal.ZERO)
                    .pointCount(records.size())
                    .build();
        }

        double totalDistance = 0;
        double maxSpeed = 0;
        double totalSpeed = 0;
        int speedCount = 0;

        for (int i = 1; i < records.size(); i++) {
            GpsRecord prev = records.get(i - 1);
            GpsRecord curr = records.get(i);

            double dist = haversineDistance(
                    prev.getLatitude().doubleValue(), prev.getLongitude().doubleValue(),
                    curr.getLatitude().doubleValue(), curr.getLongitude().doubleValue());

            // 过滤GPS漂移（单次移动超过100km视为异常）
            if (dist < 100) {
                totalDistance += dist;
            }

            if (curr.getSpeed() != null) {
                double speed = curr.getSpeed().doubleValue();
                maxSpeed = Math.max(maxSpeed, speed);
                totalSpeed += speed;
                speedCount++;
            }
        }

        return MileageResult.builder()
                .vehicleId(vehicleId)
                .totalMileage(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP))
                .maxSpeed(BigDecimal.valueOf(maxSpeed).setScale(1, RoundingMode.HALF_UP))
                .avgSpeed(speedCount > 0
                        ? BigDecimal.valueOf(totalSpeed / speedCount).setScale(1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .pointCount(records.size())
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    /**
     * 创建电子围栏
     *
     * @param fence 围栏定义
     */
    @Operation(summary = "创建电子围栏")
    public void createGeofence(Geofence fence) {
        if (fence.getCenterLon() == null || fence.getCenterLat() == null || fence.getRadiusKm() == null) {
            throw new IllegalArgumentException("围栏中心坐标和半径不能为空");
        }

        String key = FENCE_KEY_PREFIX + fence.getFenceId();
        Map<String, String> fenceData = new HashMap<>();
        fenceData.put("name", fence.getName());
        fenceData.put("centerLon", fence.getCenterLon().toString());
        fenceData.put("centerLat", fence.getCenterLat().toString());
        fenceData.put("radiusKm", fence.getRadiusKm().toString());
        fenceData.put("type", fence.getType()); // ENTER, EXIT, BOTH
        fenceData.put("vehicleIds", fence.getVehicleIds() != null ? String.join(",", fence.getVehicleIds()) : "ALL");

        redisTemplate.opsForHash().putAll(key, fenceData);
        log.info("创建电子围栏: id={}, name={}, 半径={}km", fence.getFenceId(), fence.getName(), fence.getRadiusKm());
    }

    /**
     * 检查电子围栏
     */
    private List<FenceAlert> checkGeofences(Long vehicleId, BigDecimal longitude, BigDecimal latitude) {
        List<FenceAlert> alerts = new ArrayList<>();

        // 获取所有围栏
        Set<String> fenceKeys = redisTemplate.keys(FENCE_KEY_PREFIX + "*");
        if (fenceKeys == null || fenceKeys.isEmpty()) return alerts;

        for (String key : fenceKeys) {
            Map<Object, Object> fenceData = redisTemplate.opsForHash().entries(key);
            if (fenceData.isEmpty()) continue;

            String vehicleFilter = (String) fenceData.get("vehicleIds");
            if (vehicleFilter != null && !vehicleFilter.equals("ALL")
                    && !vehicleFilter.contains(String.valueOf(vehicleId))) {
                continue; // 不在监控范围内
            }

            double centerLon = Double.parseDouble((String) fenceData.get("centerLon"));
            double centerLat = Double.parseDouble((String) fenceData.get("centerLat"));
            double radiusKm = Double.parseDouble((String) fenceData.get("radiusKm"));
            String type = (String) fenceData.get("type");
            String name = (String) fenceData.get("name");

            double distance = haversineDistance(latitude.doubleValue(), longitude.doubleValue(), centerLat, centerLon);

            // 判断进出围栏
            String prevStateKey = "geofence:state:" + vehicleId + ":" + key.replace(FENCE_KEY_PREFIX, "");
            String prevState = redisTemplate.opsForValue().get(prevStateKey);
            boolean wasInside = "INSIDE".equals(prevState);
            boolean isInside = distance <= radiusKm;

            // 更新状态
            redisTemplate.opsForValue().set(prevStateKey, isInside ? "INSIDE" : "OUTSIDE");

            // 检查是否触发告警
            if ("ENTER".equals(type) || "BOTH".equals(type)) {
                if (!wasInside && isInside) {
                    FenceAlert alert = FenceAlert.builder()
                            .fenceName(name)
                            .alertType("ENTER")
                            .vehicleId(vehicleId)
                            .distance(BigDecimal.valueOf(distance).setScale(3, RoundingMode.HALF_UP))
                            .alertTime(LocalDateTime.now())
                            .build();
                    alerts.add(alert);

                    // 发送告警消息
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("vehicleId", vehicleId);
                    msg.put("fenceName", name);
                    msg.put("alertType", "ENTER");
                    msg.put("distance", distance);
                    // rocketMQ disabled
                }
            }
            if ("EXIT".equals(type) || "BOTH".equals(type)) {
                if (wasInside && !isInside) {
                    FenceAlert alert = FenceAlert.builder()
                            .fenceName(name)
                            .alertType("EXIT")
                            .vehicleId(vehicleId)
                            .distance(BigDecimal.valueOf(distance).setScale(3, RoundingMode.HALF_UP))
                            .alertTime(LocalDateTime.now())
                            .build();
                    alerts.add(alert);

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("vehicleId", vehicleId);
                    msg.put("fenceName", name);
                    msg.put("alertType", "EXIT");
                    msg.put("distance", distance);
                    // rocketMQ disabled
                }
            }
        }

        return alerts;
    }

    /**
     * Haversine公式计算两点间球面距离(km)
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // ========== 内部类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpsReport {
        private Long vehicleId;
        private String plateNumber;
        private Long transportOrderId;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private BigDecimal speed;
        private BigDecimal direction;
        private String location;
        private LocalDateTime recordTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpsReportResult {
        private boolean success;
        private Long vehicleId;
        private List<FenceAlert> fenceAlerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpsPosition {
        private Long vehicleId;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private BigDecimal speed;
        private BigDecimal direction;
        private LocalDateTime recordTime;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MileageResult {
        private Long vehicleId;
        private BigDecimal totalMileage; // km
        private BigDecimal maxSpeed;     // km/h
        private BigDecimal avgSpeed;     // km/h
        private Integer pointCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Geofence {
        private String fenceId;
        private String name;
        private BigDecimal centerLon;
        private BigDecimal centerLat;
        private BigDecimal radiusKm;
        private String type; // ENTER, EXIT, BOTH
        private List<String> vehicleIds; // null or empty = ALL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FenceAlert {
        private String fenceName;
        private String alertType; // ENTER, EXIT
        private Long vehicleId;
        private BigDecimal distance;
        private LocalDateTime alertTime;
    }
}
