package com.orange.logistics.order.service;

import com.orange.logistics.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 订单状态机
 * 管理订单状态转换规则、守卫条件和转换动作
 *
 * 状态流转：
 * CREATED -> PENDING_PICKUP -> PICKED_UP -> IN_TRANSIT -> DELIVERING -> SIGNED -> COMPLETED
 *                                                                                 CANCELLED
 * 任何非终态 -> EXCEPTION (恢复到 IN_TRANSIT / DELIVERING / CANCELLED)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final RedissonClient redissonClient;

    /**
     * 状态转换定义：源状态 -> 允许的目标状态列表
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    /**
     * 守卫条件：某些转换需要满足额外条件
     */
    private static final Map<String, Predicate<TransitionContext>> GUARDS = new HashMap<>();

    static {
        // 定义合法的状态转换
        TRANSITIONS.put(OrderStatus.CREATED, EnumSet.of(
                OrderStatus.PENDING_PICKUP, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.PENDING_PICKUP, EnumSet.of(
                OrderStatus.PICKED_UP, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.PICKED_UP, EnumSet.of(
                OrderStatus.IN_TRANSIT, OrderStatus.EXCEPTION));
        TRANSITIONS.put(OrderStatus.IN_TRANSIT, EnumSet.of(
                OrderStatus.DELIVERING, OrderStatus.EXCEPTION));
        TRANSITIONS.put(OrderStatus.DELIVERING, EnumSet.of(
                OrderStatus.SIGNED, OrderStatus.EXCEPTION));
        TRANSITIONS.put(OrderStatus.SIGNED, EnumSet.of(
                OrderStatus.COMPLETED));
        TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.EXCEPTION, EnumSet.of(
                OrderStatus.IN_TRANSIT, OrderStatus.DELIVERING, OrderStatus.CANCELLED));

        // 守卫条件：取消订单需要在揽收前
        GUARDS.put(buildKey(OrderStatus.PENDING_PICKUP, OrderStatus.CANCELLED), ctx ->
                ctx.getReason() != null && !ctx.getReason().isBlank());

        // 守卫条件：签收需要有签收人信息
        GUARDS.put(buildKey(OrderStatus.DELIVERING, OrderStatus.SIGNED), ctx ->
                ctx.getOperator() != null && !ctx.getOperator().isBlank());

        // 守卫条件：异常恢复需要有处理说明
        GUARDS.put(buildKey(OrderStatus.EXCEPTION, OrderStatus.IN_TRANSIT), ctx ->
                ctx.getReason() != null && !ctx.getReason().isBlank());
        GUARDS.put(buildKey(OrderStatus.EXCEPTION, OrderStatus.DELIVERING), ctx ->
                ctx.getReason() != null && !ctx.getReason().isBlank());
    }

    /**
     * 执行状态转换（带分布式锁）
     *
     * @param orderId 订单ID
     * @param current 当前状态
     * @param target  目标状态
     * @param context 转换上下文
     * @return 转换结果
     */
    @Operation(summary = "执行订单状态转换")
    public TransitionResult transition(Long orderId, OrderStatus current, OrderStatus target, TransitionContext context) {
        if (orderId == null) {
            return TransitionResult.fail("订单ID不能为空");
        }
        if (current == null || target == null) {
            return TransitionResult.fail("状态不能为空");
        }

        // 1. 检查转换是否合法
        Set<OrderStatus> allowedTargets = TRANSITIONS.get(current);
        if (allowedTargets == null || !allowedTargets.contains(target)) {
            log.warn("非法状态转换: orderId={}, {} -> {}", orderId, current, target);
            return TransitionResult.fail(
                    String.format("不允许从 [%s] 转换到 [%s]", current.getDesc(), target.getDesc()));
        }

        // 2. 检查守卫条件
        String guardKey = buildKey(current, target);
        Predicate<TransitionContext> guard = GUARDS.get(guardKey);
        if (guard != null && !guard.test(context)) {
            log.warn("守卫条件不满足: orderId={}, {} -> {}", orderId, current, target);
            return TransitionResult.fail("状态转换条件不满足，请检查必填信息");
        }

        // 3. 使用分布式锁保证并发安全
        String lockKey = "order:state:lock:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                return TransitionResult.fail("获取锁超时，请稍后重试");
            }

            log.info("订单状态转换成功: orderId={}, {} -> {}, operator={}",
                    orderId, current.getDesc(), target.getDesc(), context.getOperator());

            return TransitionResult.success(current, target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TransitionResult.fail("状态转换被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 检查是否可以转换到目标状态（不执行）
     */
    @Operation(summary = "检查状态转换是否合法")
    public boolean canTransit(OrderStatus current, OrderStatus target) {
        Set<OrderStatus> allowedTargets = TRANSITIONS.get(current);
        return allowedTargets != null && allowedTargets.contains(target);
    }

    /**
     * 获取当前状态可达的所有目标状态
     */
    @Operation(summary = "获取可达状态列表")
    public Set<OrderStatus> getAvailableTransitions(OrderStatus current) {
        return TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminalState(OrderStatus status) {
        return status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED;
    }

    private static String buildKey(OrderStatus from, OrderStatus to) {
        return from.name() + "->" + to.name();
    }

    // ========== 内部类 ==========

    /**
     * 转换上下文
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransitionContext {
        private String operator;   // 操作人
    private String reason;     // 原因/备注
    private Map<String, Object> extra; // 扩展信息
    }

    /**
     * 转换结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransitionResult {
        private boolean success;
        private String message;
        private OrderStatus fromStatus;
        private OrderStatus toStatus;

        public static TransitionResult success(OrderStatus from, OrderStatus to) {
            return TransitionResult.builder()
                    .success(true)
                    .message("状态转换成功")
                    .fromStatus(from)
                    .toStatus(to)
                    .build();
        }

        public static TransitionResult fail(String message) {
            return TransitionResult.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
