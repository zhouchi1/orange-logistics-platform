package com.orange.logistics.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.order.entity.LogisticsOrder;
import com.orange.logistics.order.enums.OrderStatus;
import com.orange.logistics.order.repository.OrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// \ // disabled
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 货到付款(COD)处理服务
 * 处理代收款确认、退款等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodService {

    private final OrderMapper orderMapper;
    private final RedissonClient redissonClient;
    // private final RocketMQTemplate rocketMQTemplate; // disabled

    /**
     * 支付方式：货到付款
     */
    private static final int PAYMENT_METHOD_COD = 2;

    /**
     * 确认代收款
     * 快递员收到货款后调用此方法确认
     *
     * @param orderId     订单ID
     * @param amount      实收金额
     * @param courierId   快递员ID
     * @param courierName 快递员姓名
     */
    @Operation(summary = "确认代收款")
    @Transactional
    public CodResult confirmCollection(Long orderId, BigDecimal amount, Long courierId, String courierName) {
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("收款金额必须大于0");
        }

        String lockKey = "cod:confirm:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new RuntimeException("操作频繁，请稍后重试");
            }

            LogisticsOrder order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new IllegalArgumentException("订单不存在: " + orderId);
            }
            if (order.getPaymentMethod() != PAYMENT_METHOD_COD) {
                throw new IllegalStateException("非货到付款订单，不能进行代收款操作");
            }
            if (order.getStatus() != OrderStatus.DELIVERING.getCode()
                    && order.getStatus() != OrderStatus.SIGNED.getCode()) {
                throw new IllegalStateException("当前订单状态不允许收款操作，状态: "
                        + OrderStatus.fromCode(order.getStatus()).getDesc());
            }

            // 校验金额是否匹配
            BigDecimal expectedAmount = order.getCodAmount();
            if (expectedAmount != null && amount.compareTo(expectedAmount) != 0) {
                log.warn("COD收款金额不匹配: orderId={}, 应收={}, 实收={}",
                        orderId, expectedAmount, amount);
                // 允许差额1元以内（找零场景）
                BigDecimal diff = amount.subtract(expectedAmount).abs();
                if (diff.compareTo(BigDecimal.ONE) > 0) {
                    return CodResult.builder()
                            .success(false)
                            .orderId(orderId)
                            .message(String.format("收款金额不匹配，应收%.2f元，实收%.2f元",
                                    expectedAmount.doubleValue(), amount.doubleValue()))
                            .build();
                }
            }

            // 更新订单状态为已签收
            order.setStatus(OrderStatus.SIGNED.getCode());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);

            // 发送COD收款成功消息
            Map<String, Object> msg = new HashMap<>();
            msg.put("orderId", orderId);
            msg.put("orderNo", order.getOrderNo());
            msg.put("amount", amount);
            msg.put("courierId", courierId);
            msg.put("courierName", courierName);
            msg.put("collectTime", LocalDateTime.now().toString());
            // rocketMQ disabled for local dev

            log.info("COD代收款确认成功: orderId={}, 金额={}, 快递员={}",
                    orderId, amount, courierName);

            return CodResult.builder()
                    .success(true)
                    .orderId(orderId)
                    .orderNo(order.getOrderNo())
                    .collectedAmount(amount)
                    .message("代收款确认成功")
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * COD拒收处理
     * 收件人拒绝付款，拒收包裹
     *
     * @param orderId 订单ID
     * @param reason  拒收原因
     */
    @Operation(summary = "COD拒收处理")
    @Transactional
    public CodResult rejectCollection(Long orderId, String reason) {
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }

        LogisticsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderId);
        }
        if (order.getPaymentMethod() != PAYMENT_METHOD_COD) {
            throw new IllegalStateException("非货到付款订单");
        }

        // 更新订单状态为异常
        order.setStatus(OrderStatus.EXCEPTION.getCode());
        order.setRemark("COD拒收: " + (reason != null ? reason : "收件人拒绝付款"));
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 发送拒收消息（触发退回流程）
        Map<String, Object> msg = new HashMap<>();
        msg.put("orderId", orderId);
        msg.put("orderNo", order.getOrderNo());
        msg.put("reason", reason);
        msg.put("codAmount", order.getCodAmount());
        msg.put("rejectTime", LocalDateTime.now().toString());
        // rocketMQ disabled for local dev

        log.info("COD拒收处理: orderId={}, 原因={}", orderId, reason);

        return CodResult.builder()
                .success(true)
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .message("拒收处理完成，包裹将退回发件人")
                .build();
    }

    /**
     * COD退款
     * 订单取消或异常时，退还已收取的代收款
     *
     * @param orderId      订单ID
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     */
    @Operation(summary = "COD退款")
    @Transactional
    public CodResult refund(Long orderId, BigDecimal refundAmount, String refundReason) {
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款金额必须大于0");
        }

        String lockKey = "cod:refund:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new RuntimeException("操作频繁，请稍后重试");
            }

            LogisticsOrder order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new IllegalArgumentException("订单不存在: " + orderId);
            }
            if (order.getPaymentMethod() != PAYMENT_METHOD_COD) {
                throw new IllegalStateException("非货到付款订单，不能进行退款操作");
            }

            // 校验退款金额不超过代收金额
            BigDecimal codAmount = order.getCodAmount();
            if (codAmount != null && refundAmount.compareTo(codAmount) > 0) {
                throw new IllegalArgumentException(
                        String.format("退款金额(%.2f)不能超过代收金额(%.2f)",
                                refundAmount.doubleValue(), codAmount.doubleValue()));
            }

            // 发送退款消息（由支付系统处理实际退款）
            Map<String, Object> msg = new HashMap<>();
            msg.put("orderId", orderId);
            msg.put("orderNo", order.getOrderNo());
            msg.put("customerId", order.getCustomerId());
            msg.put("refundAmount", refundAmount);
            msg.put("refundReason", refundReason);
            msg.put("refundTime", LocalDateTime.now().toString());
            // rocketMQ disabled for local dev

            log.info("COD退款发起: orderId={}, 退款金额={}, 原因={}",
                    orderId, refundAmount, refundReason);

            return CodResult.builder()
                    .success(true)
                    .orderId(orderId)
                    .orderNo(order.getOrderNo())
                    .refundAmount(refundAmount)
                    .message("退款申请已提交，预计1-3个工作日到账")
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 查询COD订单状态
     */
    @Operation(summary = "查询COD订单收款状态")
    public CodResult getCodStatus(Long orderId) {
        LogisticsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getPaymentMethod() != PAYMENT_METHOD_COD) {
            throw new IllegalStateException("非货到付款订单");
        }

        String statusDesc = switch (order.getStatus()) {
            case 0, 1, 2, 3 -> "待收款";
            case 4 -> "派送中，待收款";
            case 5 -> "已收款";
            case 6 -> "已完成";
            case 7 -> "已取消";
            case 8 -> "异常（可能拒收）";
            default -> "未知";
        };

        return CodResult.builder()
                .success(true)
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .collectedAmount(order.getStatus() >= 5 ? order.getCodAmount() : null)
                .message(statusDesc)
                .build();
    }

    // ========== 内部类 ==========

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CodResult {
        private boolean success;
        private Long orderId;
        private String orderNo;
        private BigDecimal collectedAmount;
        private BigDecimal refundAmount;
        private String message;
    }
}
