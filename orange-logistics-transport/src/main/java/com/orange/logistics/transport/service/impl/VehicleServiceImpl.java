package com.orange.logistics.transport.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.VehicleDTO;
import com.orange.logistics.transport.entity.Vehicle;
import com.orange.logistics.transport.enums.VehicleStatus;
import com.orange.logistics.transport.repository.VehicleMapper;
import com.orange.logistics.transport.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional
    public Vehicle addVehicle(VehicleDTO dto) {
        // 检查车牌号是否已存在
        Vehicle existing = vehicleMapper.selectOne(
                new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getPlateNumber, dto.getPlateNumber()));
        if (existing != null) {
            throw new RuntimeException("车牌号已存在: " + dto.getPlateNumber());
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(dto.getPlateNumber());
        vehicle.setVehicleType(dto.getVehicleType());
        vehicle.setMaxLoad(dto.getMaxLoad());
        vehicle.setMaxVolume(dto.getMaxVolume());
        vehicle.setFleetName(dto.getFleetName());
        vehicle.setDriverId(dto.getDriverId());
        vehicle.setDriverName(dto.getDriverName());
        vehicle.setDriverPhone(dto.getDriverPhone());
        vehicle.setStatus(VehicleStatus.IDLE.getCode());
        vehicle.setMileage(BigDecimal.ZERO);
        vehicle.setDeleted(0);
        vehicle.setCreateTime(LocalDateTime.now());
        vehicle.setUpdateTime(LocalDateTime.now());
        vehicleMapper.insert(vehicle);

        log.info("新增车辆: {} - {}", vehicle.getPlateNumber(), vehicle.getVehicleType());
        return vehicle;
    }

    @Override
    @Transactional
    public Vehicle updateVehicle(Long id, VehicleDTO dto) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        vehicle.setPlateNumber(dto.getPlateNumber());
        vehicle.setVehicleType(dto.getVehicleType());
        vehicle.setMaxLoad(dto.getMaxLoad());
        vehicle.setMaxVolume(dto.getMaxVolume());
        vehicle.setFleetName(dto.getFleetName());
        vehicle.setDriverId(dto.getDriverId());
        vehicle.setDriverName(dto.getDriverName());
        vehicle.setDriverPhone(dto.getDriverPhone());
        vehicle.setUpdateTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        return vehicle;
    }

    @Override
    public Vehicle getById(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        return vehicle;
    }

    @Override
    public Vehicle getByPlateNumber(String plateNumber) {
        Vehicle vehicle = vehicleMapper.selectOne(
                new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getPlateNumber, plateNumber));
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在: " + plateNumber);
        }
        return vehicle;
    }

    @Override
    public Page<Vehicle> pageVehicles(Integer status, String fleetName, int page, int size) {
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Vehicle::getStatus, status);
        }
        if (fleetName != null && !fleetName.isEmpty()) {
            wrapper.eq(Vehicle::getFleetName, fleetName);
        }
        wrapper.orderByDesc(Vehicle::getCreateTime);
        return vehicleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        VehicleStatus.fromCode(status); // 验证状态合法性
        vehicle.setStatus(status);
        vehicle.setUpdateTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        log.info("车辆状态更新: {} -> {}", vehicle.getPlateNumber(), VehicleStatus.fromCode(status).getDesc());
    }

    @Override
    @Transactional
    public void updateLocation(Long id, BigDecimal longitude, BigDecimal latitude, String location) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        vehicle.setLongitude(longitude);
        vehicle.setLatitude(latitude);
        vehicle.setCurrentLocation(location);
        vehicle.setUpdateTime(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }
        if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT.getCode()) {
            throw new RuntimeException("运输中的车辆不能删除");
        }
        vehicleMapper.deleteById(id);
        log.info("删除车辆: {}", vehicle.getPlateNumber());
    }
}
