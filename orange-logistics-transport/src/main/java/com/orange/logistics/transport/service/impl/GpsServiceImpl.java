package com.orange.logistics.transport.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.transport.dto.GpsReportDTO;
import com.orange.logistics.transport.dto.TemperatureReportDTO;
import com.orange.logistics.transport.entity.GpsRecord;
import com.orange.logistics.transport.entity.TemperatureRecord;
import com.orange.logistics.transport.entity.Vehicle;
import com.orange.logistics.transport.repository.GpsRecordMapper;
import com.orange.logistics.transport.repository.TemperatureRecordMapper;
import com.orange.logistics.transport.repository.VehicleMapper;
import com.orange.logistics.transport.service.GpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// \ // disabled
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GpsServiceImpl implements GpsService {

    private final GpsRecordMapper gpsRecordMapper;
    private final TemperatureRecordMapper temperatureRecordMapper;
    private final VehicleMapper vehicleMapper;
    private final StringRedisTemplate redisTemplate;
    // private final RocketMQTemplate rocketMQTemplate; // disabled

    @Override
    @Transactional
    public void reportGps(GpsReportDTO dto) {
        Vehicle vehicle = vehicleMapper.selectById(dto.getVehicleId());
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }

        GpsRecord record = new GpsRecord();
        record.setVehicleId(dto.getVehicleId());
        record.setPlateNumber(vehicle.getPlateNumber());
        record.setTransportOrderId(dto.getTransportOrderId());
        record.setLongitude(dto.getLongitude());
        record.setLatitude(dto.getLatitude());
        record.setSpeed(dto.getSpeed());
        record.setDirection(dto.getDirection());
        record.setLocation(dto.getLocation());
        record.setRecordTime(dto.getRecordTime() != null ? dto.getRecordTime() : LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        gpsRecordMapper.insert(record);

        // 更新车辆实时位置
        vehicle.setLongitude(dto.getLongitude());
        vehicle.setLatitude(dto.getLatitude());
        vehicle.setCurrentLocation(dto.getLocation());
        vehicle.setUpdateTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);

        // 缓存最新位置到Redis
        String key = "vehicle:gps:" + dto.getVehicleId();
        String value = dto.getLongitude() + "," + dto.getLatitude() + "," + LocalDateTime.now();
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public List<GpsRecord> getTrack(Long vehicleId, String startTime, String endTime) {
        LambdaQueryWrapper<GpsRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GpsRecord::getVehicleId, vehicleId);
        if (startTime != null) {
            wrapper.ge(GpsRecord::getRecordTime, LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (endTime != null) {
            wrapper.le(GpsRecord::getRecordTime, LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        wrapper.orderByAsc(GpsRecord::getRecordTime);
        return gpsRecordMapper.selectList(wrapper);
    }

    @Override
    public GpsRecord getLatestPosition(Long vehicleId) {
        return gpsRecordMapper.selectOne(
                new LambdaQueryWrapper<GpsRecord>()
                        .eq(GpsRecord::getVehicleId, vehicleId)
                        .orderByDesc(GpsRecord::getRecordTime)
                        .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public void reportTemperature(TemperatureReportDTO dto) {
        Vehicle vehicle = vehicleMapper.selectById(dto.getVehicleId());
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }

        TemperatureRecord record = new TemperatureRecord();
        record.setTransportOrderId(dto.getTransportOrderId());
        record.setVehicleId(dto.getVehicleId());
        record.setPlateNumber(vehicle.getPlateNumber());
        record.setTemperature(dto.getTemperature());
        record.setHumidity(dto.getHumidity());
        record.setSetMinTemp(dto.getSetMinTemp());
        record.setSetMaxTemp(dto.getSetMaxTemp());
        record.setLongitude(dto.getLongitude());
        record.setLatitude(dto.getLatitude());
        record.setRecordTime(dto.getRecordTime() != null ? dto.getRecordTime() : LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());

        // 温度报警检查
        boolean alarm = false;
        String alarmReason = null;
        if (dto.getSetMinTemp() != null && dto.getTemperature().compareTo(dto.getSetMinTemp()) < 0) {
            alarm = true;
            alarmReason = "温度低于下限: " + dto.getTemperature() + " < " + dto.getSetMinTemp();
        } else if (dto.getSetMaxTemp() != null && dto.getTemperature().compareTo(dto.getSetMaxTemp()) > 0) {
            alarm = true;
            alarmReason = "温度高于上限: " + dto.getTemperature() + " > " + dto.getSetMaxTemp();
        }
        record.setAlarm(alarm);
        record.setAlarmReason(alarmReason);
        temperatureRecordMapper.insert(record);

        // 温度报警时发送消息
        if (alarm) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("vehicleId", dto.getVehicleId());
            msg.put("plateNumber", vehicle.getPlateNumber());
            msg.put("transportOrderId", dto.getTransportOrderId());
            msg.put("temperature", dto.getTemperature());
            msg.put("alarmReason", alarmReason);
            // rocketMQ disabled
            log.warn("冷链温度报警: 车辆={} 原因={}", vehicle.getPlateNumber(), alarmReason);
        }
    }

    @Override
    public List<TemperatureRecord> getTemperatureHistory(Long transportOrderId) {
        return temperatureRecordMapper.selectList(
                new LambdaQueryWrapper<TemperatureRecord>()
                        .eq(TemperatureRecord::getTransportOrderId, transportOrderId)
                        .orderByAsc(TemperatureRecord::getRecordTime));
    }
}
