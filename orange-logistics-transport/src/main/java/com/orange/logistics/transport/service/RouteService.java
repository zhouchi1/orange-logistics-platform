package com.orange.logistics.transport.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.RouteDTO;
import com.orange.logistics.transport.entity.TransportRoute;

public interface RouteService {
    TransportRoute createRoute(RouteDTO dto);
    TransportRoute updateRoute(Long id, RouteDTO dto);
    TransportRoute getById(Long id);
    Page<TransportRoute> pageRoutes(String originCity, String destCity, int page, int size);
    TransportRoute planRoute(String originCity, String destCity);
    void disableRoute(Long id);
}
