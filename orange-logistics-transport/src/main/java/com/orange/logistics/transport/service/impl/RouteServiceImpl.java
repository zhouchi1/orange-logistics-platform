package com.orange.logistics.transport.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.RouteDTO;
import com.orange.logistics.transport.entity.TransportRoute;
import com.orange.logistics.transport.repository.TransportRouteMapper;
import com.orange.logistics.transport.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final TransportRouteMapper routeMapper;

    @Override
    @Transactional
    public TransportRoute createRoute(RouteDTO dto) {
        TransportRoute route = new TransportRoute();
        route.setRouteCode(dto.getRouteCode());
        route.setRouteName(dto.getRouteName());
        route.setOriginStation(dto.getOriginStation());
        route.setOriginCity(dto.getOriginCity());
        route.setOriginLongitude(dto.getOriginLongitude());
        route.setOriginLatitude(dto.getOriginLatitude());
        route.setDestStation(dto.getDestStation());
        route.setDestCity(dto.getDestCity());
        route.setDestLongitude(dto.getDestLongitude());
        route.setDestLatitude(dto.getDestLatitude());
        route.setDistance(dto.getDistance());
        route.setEstimatedHours(dto.getEstimatedHours());
        route.setWaypoints(dto.getWaypoints());
        route.setRemark(dto.getRemark());
        route.setStatus(1);
        route.setDeleted(0);
        route.setCreateTime(LocalDateTime.now());
        route.setUpdateTime(LocalDateTime.now());
        routeMapper.insert(route);
        log.info("创建线路: {} ({} -> {})", route.getRouteName(), route.getOriginStation(), route.getDestStation());
        return route;
    }

    @Override
    @Transactional
    public TransportRoute updateRoute(Long id, RouteDTO dto) {
        TransportRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new RuntimeException("线路不存在");
        }
        route.setRouteCode(dto.getRouteCode());
        route.setRouteName(dto.getRouteName());
        route.setOriginStation(dto.getOriginStation());
        route.setOriginCity(dto.getOriginCity());
        route.setOriginLongitude(dto.getOriginLongitude());
        route.setOriginLatitude(dto.getOriginLatitude());
        route.setDestStation(dto.getDestStation());
        route.setDestCity(dto.getDestCity());
        route.setDestLongitude(dto.getDestLongitude());
        route.setDestLatitude(dto.getDestLatitude());
        route.setDistance(dto.getDistance());
        route.setEstimatedHours(dto.getEstimatedHours());
        route.setWaypoints(dto.getWaypoints());
        route.setRemark(dto.getRemark());
        route.setUpdateTime(LocalDateTime.now());
        routeMapper.updateById(route);
        return route;
    }

    @Override
    public TransportRoute getById(Long id) {
        TransportRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new RuntimeException("线路不存在");
        }
        return route;
    }

    @Override
    public Page<TransportRoute> pageRoutes(String originCity, String destCity, int page, int size) {
        LambdaQueryWrapper<TransportRoute> wrapper = new LambdaQueryWrapper<>();
        if (originCity != null && !originCity.isEmpty()) {
            wrapper.eq(TransportRoute::getOriginCity, originCity);
        }
        if (destCity != null && !destCity.isEmpty()) {
            wrapper.eq(TransportRoute::getDestCity, destCity);
        }
        wrapper.eq(TransportRoute::getStatus, 1);
        wrapper.orderByDesc(TransportRoute::getCreateTime);
        return routeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public TransportRoute planRoute(String originCity, String destCity) {
        // 查找已有线路
        TransportRoute route = routeMapper.selectOne(
                new LambdaQueryWrapper<TransportRoute>()
                        .eq(TransportRoute::getOriginCity, originCity)
                        .eq(TransportRoute::getDestCity, destCity)
                        .eq(TransportRoute::getStatus, 1)
                        .last("LIMIT 1"));
        if (route != null) {
            return route;
        }
        // 如果没有直达线路，返回null（实际场景可做中转规划）
        log.warn("未找到 {} -> {} 的直达线路", originCity, destCity);
        return null;
    }

    @Override
    @Transactional
    public void disableRoute(Long id) {
        TransportRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new RuntimeException("线路不存在");
        }
        route.setStatus(2);
        route.setUpdateTime(LocalDateTime.now());
        routeMapper.updateById(route);
        log.info("停用线路: {}", route.getRouteName());
    }
}
