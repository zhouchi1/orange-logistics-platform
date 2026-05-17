package com.orange.logistics.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.order.dto.CreateOrderDTO;
import com.orange.logistics.order.dto.OrderSplitDTO;
import com.orange.logistics.order.entity.LogisticsOrder;
import com.orange.logistics.order.entity.OrderStatusLog;
import com.orange.logistics.order.enums.OrderStatus;
import com.orange.logistics.order.feign.BillingFeignClient;
import com.orange.logistics.order.feign.WaybillFeignClient;
import com.orange.logistics.order.repository.OrderMapper;
import com.orange.logistics.order.repository.OrderStatusLogMapper;
import com.orange.logistics.order.service.OrderService;
import com.orange.logistics.order.vo.OrderStatusLogVO;
import com.orange.logistics.order.vo.OrderVO;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// \ // disabled
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final BillingFeignClient billingFeignClient;
    private final WaybillFeignClient waybillFeignClient;
    // private final RocketMQTemplate rocketMQTemplate; // disabled
    private final RedissonClient redissonClient;

    @Override
    @Transactional
    public OrderVO createOrder(CreateOrderDTO dto) {
        // 生成订单号: OG + 日期 + 雪花ID
        String orderNo = generateOrderNo();

        LogisticsOrder order = new LogisticsOrder();
        order.setOrderNo(orderNo);
        order.setCustomerId(dto.getCustomerId());
        order.setSenderName(dto.getSenderName());
        order.setSenderPhone(dto.getSenderPhone());
        order.setSenderAddress(dto.getSenderAddress());
        order.setSenderProvince(dto.getSenderProvince());
        order.setSenderCity(dto.getSenderCity());
        order.setSenderDistrict(dto.getSenderDistrict());
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setReceiverProvince(dto.getReceiverProvince());
        order.setReceiverCity(dto.getReceiverCity());
        order.setReceiverDistrict(dto.getReceiverDistrict());
        order.setWeight(dto.getWeight());
        order.setVolume(dto.getVolume());
        order.setItemCount(dto.getItemCount() != null ? dto.getItemCount() : 1);
        order.setItemDescription(dto.getItemDescription());
        order.setDeclaredValue(dto.getDeclaredValue());
        order.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : 1);
        order.setCodAmount(dto.getCodAmount());
        order.setServiceType(dto.getServiceType() != null ? dto.getServiceType() : 1);
        order.setRemark(dto.getRemark());
        order.setExpectedDeliveryTime(dto.getExpectedDeliveryTime());
        order.setStatus(OrderStatus.CREATED.getCode());
        order.setDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 调用计费服务计算运费
        try {
            Map<String, Object> billingParams = new HashMap<>();
            billingParams.put("weight", dto.getWeight());
            billingParams.put("volume", dto.getVolume());
            billingParams.put("senderCity", dto.getSenderCity());
            billingParams.put("receiverCity", dto.getReceiverCity());
            billingParams.put("serviceType", dto.getServiceType());
            Map<String, Object> billingResult = billingFeignClient.calculateFreight(billingParams);
            if (billingResult != null && billingResult.get("freight") != null) {
                order.setFreight(new BigDecimal(billingResult.get("freight").toString()));
            }
        } catch (Exception e) {
            log.warn("计费服务调用失败，使用默认运费: {}", e.getMessage());
            order.setFreight(BigDecimal.TEN); // 默认运费
        }

        orderMapper.insert(order);

        // 记录状态日志
        saveStatusLog(order.getId(), orderNo, null, OrderStatus.CREATED.getCode(), "系统", "订单创建");

        // 发送订单创建消息
        // rocketMQ disabled for local dev
        log.info("订单创建成功: {}", orderNo);

        return convertToVO(order);
    }

    @Override
    public OrderVO getOrderById(Long id) {
        LogisticsOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        OrderVO vo = convertToVO(order);
        // 查询状态日志
        List<OrderStatusLog> logs = statusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderId, id)
                        .orderByAsc(OrderStatusLog::getCreateTime)
        );
        vo.setStatusLogs(logs.stream().map(this::convertLogToVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public OrderVO getOrderByNo(String orderNo) {
        LogisticsOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<LogisticsOrder>()
                        .eq(LogisticsOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return convertToVO(order);
    }

    @Override
    public Page<OrderVO> pageOrders(Long customerId, Integer status, int page, int size) {
        Page<LogisticsOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(LogisticsOrder::getCustomerId, customerId);
        }
        if (status != null) {
            wrapper.eq(LogisticsOrder::getStatus, status);
        }
        wrapper.orderByDesc(LogisticsOrder::getCreateTime);

        Page<LogisticsOrder> result = orderMapper.selectPage(pageParam, wrapper);
        Page<OrderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        LogisticsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());
        if (!currentStatus.canTransitTo(OrderStatus.CANCELLED)) {
            throw new RuntimeException("当前状态不允许取消: " + currentStatus.getDesc());
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        saveStatusLog(orderId, order.getOrderNo(), currentStatus.getCode(),
                OrderStatus.CANCELLED.getCode(), "用户", reason);

        // rocketMQ disabled for local dev
        log.info("订单取消成功: {}", order.getOrderNo());
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, Integer targetStatus, String operator, String remark) {
        RLock lock = redissonClient.getLock("order:status:" + orderId);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                LogisticsOrder order = orderMapper.selectById(orderId);
                if (order == null) {
                    throw new RuntimeException("订单不存在");
                }

                OrderStatus current = OrderStatus.fromCode(order.getStatus());
                OrderStatus target = OrderStatus.fromCode(targetStatus);

                if (!current.canTransitTo(target)) {
                    throw new RuntimeException(
                            String.format("状态转换不合法: %s -> %s", current.getDesc(), target.getDesc()));
                }

                order.setStatus(targetStatus);
                order.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(order);

                saveStatusLog(orderId, order.getOrderNo(), current.getCode(), targetStatus, operator, remark);

                // 发送状态变更消息
                Map<String, Object> msg = new HashMap<>();
                msg.put("orderId", orderId);
                msg.put("orderNo", order.getOrderNo());
                msg.put("fromStatus", current.getCode());
                msg.put("toStatus", targetStatus);
                // rocketMQ disabled for local dev

                log.info("订单状态更新: {} -> {}, 订单号: {}", current.getDesc(), target.getDesc(), order.getOrderNo());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public List<OrderVO> splitOrder(OrderSplitDTO dto) {
        LogisticsOrder parentOrder = orderMapper.selectById(dto.getOrderId());
        if (parentOrder == null) {
            throw new RuntimeException("原订单不存在");
        }
        if (parentOrder.getStatus() != OrderStatus.CREATED.getCode()) {
            throw new RuntimeException("只有已创建状态的订单才能拆分");
        }

        List<OrderVO> splitOrders = new ArrayList<>();
        for (OrderSplitDTO.SplitItem item : dto.getSplitItems()) {
            LogisticsOrder subOrder = new LogisticsOrder();
            subOrder.setOrderNo(generateOrderNo());
            subOrder.setCustomerId(parentOrder.getCustomerId());
            subOrder.setSenderName(parentOrder.getSenderName());
            subOrder.setSenderPhone(parentOrder.getSenderPhone());
            subOrder.setSenderAddress(parentOrder.getSenderAddress());
            subOrder.setSenderProvince(parentOrder.getSenderProvince());
            subOrder.setSenderCity(parentOrder.getSenderCity());
            subOrder.setSenderDistrict(parentOrder.getSenderDistrict());
            subOrder.setReceiverName(parentOrder.getReceiverName());
            subOrder.setReceiverPhone(parentOrder.getReceiverPhone());
            subOrder.setReceiverAddress(parentOrder.getReceiverAddress());
            subOrder.setReceiverProvince(parentOrder.getReceiverProvince());
            subOrder.setReceiverCity(parentOrder.getReceiverCity());
            subOrder.setReceiverDistrict(parentOrder.getReceiverDistrict());
            subOrder.setWeight(item.getWeight());
            subOrder.setItemCount(item.getItemCount());
            subOrder.setItemDescription(item.getItemDescription());
            subOrder.setPaymentMethod(parentOrder.getPaymentMethod());
            subOrder.setServiceType(parentOrder.getServiceType());
            subOrder.setParentOrderId(parentOrder.getId());
            subOrder.setStatus(OrderStatus.CREATED.getCode());
            subOrder.setDeleted(0);
            subOrder.setCreateTime(LocalDateTime.now());
            subOrder.setUpdateTime(LocalDateTime.now());
            orderMapper.insert(subOrder);
            splitOrders.add(convertToVO(subOrder));
        }

        // 取消原订单
        parentOrder.setStatus(OrderStatus.CANCELLED.getCode());
        parentOrder.setRemark("订单已拆分");
        parentOrder.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(parentOrder);

        log.info("订单拆分成功: {} -> {} 个子订单", parentOrder.getOrderNo(), splitOrders.size());
        return splitOrders;
    }

    @Override
    @Transactional
    public OrderVO mergeOrders(List<Long> orderIds) {
        List<LogisticsOrder> orders = orderMapper.selectBatchIds(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new RuntimeException("部分订单不存在");
        }

        // 验证所有订单状态为已创建且收件人相同
        String receiverPhone = orders.get(0).getReceiverPhone();
        for (LogisticsOrder order : orders) {
            if (order.getStatus() != OrderStatus.CREATED.getCode()) {
                throw new RuntimeException("只有已创建状态的订单才能合并");
            }
            if (!receiverPhone.equals(order.getReceiverPhone())) {
                throw new RuntimeException("只有相同收件人的订单才能合并");
            }
        }

        // 创建合并订单
        LogisticsOrder first = orders.get(0);
        LogisticsOrder mergedOrder = new LogisticsOrder();
        mergedOrder.setOrderNo(generateOrderNo());
        mergedOrder.setCustomerId(first.getCustomerId());
        mergedOrder.setSenderName(first.getSenderName());
        mergedOrder.setSenderPhone(first.getSenderPhone());
        mergedOrder.setSenderAddress(first.getSenderAddress());
        mergedOrder.setSenderProvince(first.getSenderProvince());
        mergedOrder.setSenderCity(first.getSenderCity());
        mergedOrder.setSenderDistrict(first.getSenderDistrict());
        mergedOrder.setReceiverName(first.getReceiverName());
        mergedOrder.setReceiverPhone(first.getReceiverPhone());
        mergedOrder.setReceiverAddress(first.getReceiverAddress());
        mergedOrder.setReceiverProvince(first.getReceiverProvince());
        mergedOrder.setReceiverCity(first.getReceiverCity());
        mergedOrder.setReceiverDistrict(first.getReceiverDistrict());
        mergedOrder.setWeight(orders.stream().map(LogisticsOrder::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add));
        mergedOrder.setItemCount(orders.stream().mapToInt(o -> o.getItemCount() != null ? o.getItemCount() : 1).sum());
        mergedOrder.setPaymentMethod(first.getPaymentMethod());
        mergedOrder.setServiceType(first.getServiceType());
        mergedOrder.setStatus(OrderStatus.CREATED.getCode());
        mergedOrder.setDeleted(0);
        mergedOrder.setCreateTime(LocalDateTime.now());
        mergedOrder.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(mergedOrder);

        // 取消原订单
        for (LogisticsOrder order : orders) {
            order.setStatus(OrderStatus.CANCELLED.getCode());
            order.setRemark("已合并至订单: " + mergedOrder.getOrderNo());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
        }

        log.info("订单合并成功: {} 个订单 -> {}", orders.size(), mergedOrder.getOrderNo());
        return convertToVO(mergedOrder);
    }

    @Override
    @Transactional
    public void processCodPayment(Long orderId, boolean paid) {
        LogisticsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getPaymentMethod() != 2) {
            throw new RuntimeException("非货到付款订单");
        }

        if (paid) {
            // COD已收款，更新状态为已签收
            updateOrderStatus(orderId, OrderStatus.SIGNED.getCode(), "快递员", "货到付款已收款");
        } else {
            // COD拒收
            updateOrderStatus(orderId, OrderStatus.EXCEPTION.getCode(), "快递员", "货到付款拒收");
        }
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "OG" + date + snowflake.substring(snowflake.length() - 8);
    }

    private void saveStatusLog(Long orderId, String orderNo, Integer fromStatus, Integer toStatus,
                               String operator, String remark) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setOrderNo(orderNo);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperator(operator);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        statusLogMapper.insert(log);
    }

    private OrderVO convertToVO(LogisticsOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setSenderName(order.getSenderName());
        vo.setSenderPhone(order.getSenderPhone());
        vo.setSenderAddress(order.getSenderAddress());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setWeight(order.getWeight());
        vo.setVolume(order.getVolume());
        vo.setItemCount(order.getItemCount());
        vo.setItemDescription(order.getItemDescription());
        vo.setFreight(order.getFreight());
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setCodAmount(order.getCodAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.fromCode(order.getStatus()).getDesc());
        vo.setServiceType(order.getServiceType());
        vo.setWaybillNo(order.getWaybillNo());
        vo.setExpectedDeliveryTime(order.getExpectedDeliveryTime());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    private OrderStatusLogVO convertLogToVO(OrderStatusLog log) {
        OrderStatusLogVO vo = new OrderStatusLogVO();
        vo.setFromStatus(log.getFromStatus());
        if (log.getFromStatus() != null) {
            vo.setFromStatusDesc(OrderStatus.fromCode(log.getFromStatus()).getDesc());
        }
        vo.setToStatus(log.getToStatus());
        vo.setToStatusDesc(OrderStatus.fromCode(log.getToStatus()).getDesc());
        vo.setOperator(log.getOperator());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
