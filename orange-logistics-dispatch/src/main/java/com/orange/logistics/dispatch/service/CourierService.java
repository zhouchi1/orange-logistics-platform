package com.orange.logistics.dispatch.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.dispatch.dto.CourierDTO;
import com.orange.logistics.dispatch.entity.Courier;

public interface CourierService {
    Courier addCourier(CourierDTO dto);
    Courier updateCourier(Long id, CourierDTO dto);
    Courier getById(Long id);
    Page<Courier> pageCouriers(Integer status, Long stationId, int page, int size);
    void updateStatus(Long id, Integer status);
    void updateLocation(Long id, java.math.BigDecimal longitude, java.math.BigDecimal latitude);
    void deleteCourier(Long id);
}
