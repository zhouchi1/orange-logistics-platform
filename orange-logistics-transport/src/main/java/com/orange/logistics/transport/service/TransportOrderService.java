package com.orange.logistics.transport.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.transport.dto.CreateTransportOrderDTO;
import com.orange.logistics.transport.entity.TransportOrder;

public interface TransportOrderService {
    TransportOrder createOrder(CreateTransportOrderDTO dto);
    TransportOrder getById(Long id);
    TransportOrder getByTransportNo(String transportNo);
    Page<TransportOrder> pageOrders(Long vehicleId, Integer status, int page, int size);
    void depart(Long id);
    void arrive(Long id);
    void markException(Long id, String remark);
    void cancel(Long id, String reason);
}
