package com.orange.logistics.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.order.dto.CreateOrderDTO;
import com.orange.logistics.order.dto.OrderSplitDTO;
import com.orange.logistics.order.vo.OrderVO;

import java.util.List;

public interface OrderService {
    OrderVO createOrder(CreateOrderDTO dto);
    OrderVO getOrderById(Long id);
    OrderVO getOrderByNo(String orderNo);
    Page<OrderVO> pageOrders(Long customerId, Integer status, int page, int size);
    void cancelOrder(Long orderId, String reason);
    void updateOrderStatus(Long orderId, Integer targetStatus, String operator, String remark);
    List<OrderVO> splitOrder(OrderSplitDTO dto);
    OrderVO mergeOrders(List<Long> orderIds);
    void processCodPayment(Long orderId, boolean paid);
}
