package com.orange.logistics.transport.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.CreateTransportOrderDTO;
import com.orange.logistics.transport.entity.TransportOrder;
import com.orange.logistics.transport.entity.Vehicle;
import com.orange.logistics.transport.enums.TransportStatus;
import com.orange.logistics.transport.enums.VehicleStatus;
import com.orange.logistics.transport.repository.TransportOrderMapper;
import com.orange.logistics.transport.repository.VehicleMapper;
import com.orange.logistics.transport.service.TransportOrderService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// \ // disabled
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransportOrderServiceImpl implements TransportOrderService {

    private final TransportOrderMapper transportOrderMapper;
    private final VehicleMapper vehicleMapper;
    // private final RocketMQTemplate rocketMQTemplate; // disabled

    @Override
    @Transactional
    public TransportOrder createOrder(CreateTransportOrderDTO dto) {
        Vehicle vehicle = vehicleMapper.selectById(dto.getVehicleId());
        if (vehicle == null) {
            throw new RuntimeException("车辆不存在");
        }

        TransportOrder order = new TransportOrder();
        order.setTransportNo(generateTransportNo());
        order.setWaybillNo(dto.getWaybillNo());
        order.setVehicleId(dto.getVehicleId());
        order.setPlateNumber(vehicle.getPlateNumber());
        order.setDriverId(dto.getDriverId() != null ? dto.getDriverId() : vehicle.getDriverId());
        order.setDriverName(vehicle.getDriverName());
        order.setDriverPhone(vehicle.getDriverPhone());
        order.setOriginStation(dto.getOriginStation());
        order.setOriginAddress(dto.getOriginAddress());
        order.setOriginLongitude(dto.getOriginLongitude());
        order.setOriginLatitude(dto.getOriginLatitude());
        order.setDestStation(dto.getDestStation());
        order.setDestAddress(dto.getDestAddress());
        order.setDestLongitude(dto.getDestLongitude());
        order.setDestLatitude(dto.getDestLatitude());
        order.setRouteId(dto.getRouteId());
        order.setCargoDescription(dto.getCargoDescription());
        order.setCargoWeight(dto.getCargoWeight());
        order.setCargoCount(dto.getCargoCount());
        order.setPlannedDepartTime(dto.getPlannedDepartTime());
        order.setPlannedArriveTime(dto.getPlannedArriveTime());
        order.setRemark(dto.getRemark());
        order.setStatus(TransportStatus.PENDING.getCode());
        order.setDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        transportOrderMapper.insert(order);

        log.info("创建运输单: {} 车辆: {}", order.getTransportNo(), vehicle.getPlateNumber());
        return order;
    }

    @Override
    public TransportOrder getById(Long id) {
        TransportOrder order = transportOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("运输单不存在");
        }
        return order;
    }

    @Override
    public TransportOrder getByTransportNo(String transportNo) {
        TransportOrder order = transportOrderMapper.selectOne(
                new LambdaQueryWrapper<TransportOrder>().eq(TransportOrder::getTransportNo, transportNo));
        if (order == null) {
            throw new RuntimeException("运输单不存在: " + transportNo);
        }
        return order;
    }

    @Override
    public Page<TransportOrder> pageOrders(Long vehicleId, Integer status, int page, int size) {
        LambdaQueryWrapper<TransportOrder> wrapper = new LambdaQueryWrapper<>();
        if (vehicleId != null) {
            wrapper.eq(TransportOrder::getVehicleId, vehicleId);
        }
        if (status != null) {
            wrapper.eq(TransportOrder::getStatus, status);
        }
        wrapper.orderByDesc(TransportOrder::getCreateTime);
        return transportOrderMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void depart(Long id) {
        TransportOrder order = transportOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("运输单不存在");
        }
        if (order.getStatus() != TransportStatus.PENDING.getCode()) {
            throw new RuntimeException("当前状态不允许发车");
        }
        order.setStatus(TransportStatus.IN_TRANSIT.getCode());
        order.setActualDepartTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        transportOrderMapper.updateById(order);

        // 更新车辆状态为运输中中
        Vehicle vehicle = vehicleMapper.selectById(order.getVehicleId());
        if (vehicle != null) {
            vehicle.setStatus(VehicleStatus.IN_TRANSIT.getCode());
            vehicle.setUpdateTime(LocalDateTime.now());
            vehicleMapper.updateById(vehicle);
        }

        // 发送发车消息息
        Map<String, Object> msg = new HashMap<>();
        msg.put("transportNo", order.getTransportNo());
        msg.put("event", "DEPART");
        // rocketMQ disabled
        log.info("运输单发车: {}", order.getTransportNo());
    }

    @Override
    @Transactional
    public void arrive(Long id) {
        TransportOrder order = transportOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("运输单不存在");
        }
        if (order.getStatus() != TransportStatus.IN_TRANSIT.getCode()) {
            throw new RuntimeException("当前状态不允许到达确认");
        }
        order.setStatus(TransportStatus.ARRIVED.getCode());
        order.setActualArriveTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        transportOrderMapper.updateById(order);

        // 更新车辆状态为空闲
        Vehicle vehicle = vehicleMapper.selectById(order.getVehicleId());
        if (vehicle != null) {
            vehicle.setStatus(VehicleStatus.IDLE.getCode());
            vehicle.setUpdateTime(LocalDateTime.now());
            vehicleMapper.updateById(vehicle);
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("transportNo", order.getTransportNo());
        msg.put("event", "ARRIVE");
        // rocketMQ disabled
        log.info("运输单到达: {}", order.getTransportNo());
    }

    @Override
    @Transactional
    public void markException(Long id, String remark) {
        TransportOrder order = transportOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("运输单不存在");
        }
        order.setStatus(TransportStatus.EXCEPTION.getCode());
        order.setRemark(remark);
        order.setUpdateTime(LocalDateTime.now());
        transportOrderMapper.updateById(order);

        Map<String, Object> msg = new HashMap<>();
        msg.put("transportNo", order.getTransportNo());
        msg.put("event", "EXCEPTION");
        msg.put("remark", remark);
        // rocketMQ disabled
        log.warn("运输单异常: {} - {}", order.getTransportNo(), remark);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        TransportOrder order = transportOrderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("运输单不存在");
        }
        if (order.getStatus() == TransportStatus.IN_TRANSIT.getCode()) {
            throw new RuntimeException("运输中的订单不能取消");
        }
        order.setStatus(TransportStatus.CANCELLED.getCode());
        order.setRemark(reason);
        order.setUpdateTime(LocalDateTime.now());
        transportOrderMapper.updateById(order);
        log.info("运输单取消: {} - {}", order.getTransportNo(), reason);
    }

    private String generateTransportNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "TP" + date + snowflake.substring(snowflake.length() - 8);
    }
}
