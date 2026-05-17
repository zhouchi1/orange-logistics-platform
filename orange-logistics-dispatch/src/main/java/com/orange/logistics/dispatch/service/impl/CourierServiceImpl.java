package com.orange.logistics.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.dispatch.dto.CourierDTO;
import com.orange.logistics.dispatch.entity.Courier;
import com.orange.logistics.dispatch.repository.CourierMapper;
import com.orange.logistics.dispatch.service.CourierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierServiceImpl implements CourierService {

    private final CourierMapper courierMapper;

    @Override
    @Transactional
    public Courier addCourier(CourierDTO dto) {
        Courier courier = new Courier();
        courier.setCourierCode(dto.getCourierCode());
        courier.setName(dto.getName());
        courier.setPhone(dto.getPhone());
        courier.setIdCard(dto.getIdCard());
        courier.setStationId(dto.getStationId());
        courier.setStationName(dto.getStationName());
        courier.setArea(dto.getArea());
        courier.setMaxLoad(dto.getMaxLoad());
        courier.setCurrentLoad(0);
        courier.setStatus(1); // 空闲
        courier.setRating(BigDecimal.valueOf(5.0));
        courier.setTotalDeliveries(0);
        courier.setTodayDeliveries(0);
        courier.setDeleted(0);
        courier.setCreateTime(LocalDateTime.now());
        courier.setUpdateTime(LocalDateTime.now());
        courierMapper.insert(courier);
        log.info("新增快递员: {} - {}", courier.getCourierCode(), courier.getName());
        return courier;
    }

    @Override
    @Transactional
    public Courier updateCourier(Long id, CourierDTO dto) {
        Courier courier = courierMapper.selectById(id);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        courier.setCourierCode(dto.getCourierCode());
        courier.setName(dto.getName());
        courier.setPhone(dto.getPhone());
        courier.setIdCard(dto.getIdCard());
        courier.setStationId(dto.getStationId());
        courier.setStationName(dto.getStationName());
        courier.setArea(dto.getArea());
        courier.setMaxLoad(dto.getMaxLoad());
        courier.setUpdateTime(LocalDateTime.now());
        courierMapper.updateById(courier);
        return courier;
    }

    @Override
    public Courier getById(Long id) {
        Courier courier = courierMapper.selectById(id);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        return courier;
    }

    @Override
    public Page<Courier> pageCouriers(Integer status, Long stationId, int page, int size) {
        LambdaQueryWrapper<Courier> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Courier::getStatus, status);
        }
        if (stationId != null) {
            wrapper.eq(Courier::getStationId, stationId);
        }
        wrapper.orderByDesc(Courier::getCreateTime);
        return courierMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Courier courier = courierMapper.selectById(id);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        courier.setStatus(status);
        courier.setUpdateTime(LocalDateTime.now());
        courierMapper.updateById(courier);
    }

    @Override
    @Transactional
    public void updateLocation(Long id, BigDecimal longitude, BigDecimal latitude) {
        Courier courier = courierMapper.selectById(id);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        courier.setLongitude(longitude);
        courier.setLatitude(latitude);
        courier.setUpdateTime(LocalDateTime.now());
        courierMapper.updateById(courier);
    }

    @Override
    @Transactional
    public void deleteCourier(Long id) {
        Courier courier = courierMapper.selectById(id);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        if (courier.getStatus() == 2) {
            throw new RuntimeException("配送中的快递员不能删除");
        }
        courierMapper.deleteById(id);
        log.info("删除快递员: {}", courier.getName());
    }
}
