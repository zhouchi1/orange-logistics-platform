package com.orange.logistics.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orange.logistics.dispatch.dto.DispatchRequestDTO;
import com.orange.logistics.dispatch.dto.SignoffDTO;
import com.orange.logistics.dispatch.entity.Courier;
import com.orange.logistics.dispatch.entity.DeliveryTask;
import com.orange.logistics.dispatch.enums.TaskStatus;
import com.orange.logistics.dispatch.repository.CourierMapper;
import com.orange.logistics.dispatch.repository.DeliveryTaskMapper;
import com.orange.logistics.dispatch.service.DispatchService;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// \ // disabled
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final DeliveryTaskMapper taskMapper;
    private final CourierMapper courierMapper;
    // private final RocketMQTemplate rocketMQTemplate; // disabled

    /**
     * 智能派单算法：基于距离+负载+时效的评分排序
     * 评分 = 距离(40%) + 负载(30%) + 时效(30%)
     */
    @Override
    @Transactional
    public DeliveryTask dispatch(DispatchRequestDTO dto) {
        // 查找可用快递员（空闲或配送中但未满载）
        List<Courier> candidates = courierMapper.selectList(
                new LambdaQueryWrapper<Courier>()
                        .in(Courier::getStatus, 1, 2) // 空闲或配送中
        );

        if (candidates.isEmpty()) {
            throw new RuntimeException("当前无可用快递员");
        }

        // 计算每个快递员的派单评分
        Courier bestCourier = selectBestCourier(candidates, dto);

        // 创建配送任务
        DeliveryTask task = new DeliveryTask();
        task.setTaskNo(generateTaskNo());
        task.setCourierId(bestCourier.getId());
        task.setCourierName(bestCourier.getName());
        task.setCourierPhone(bestCourier.getPhone());
        task.setWaybillNo(dto.getWaybillNo());
        task.setOrderNo(dto.getOrderNo());
        task.setReceiverName(dto.getReceiverName());
        task.setReceiverPhone(dto.getReceiverPhone());
        task.setReceiverAddress(dto.getReceiverAddress());
        task.setReceiverLongitude(dto.getReceiverLongitude());
        task.setReceiverLatitude(dto.getReceiverLatitude());
        task.setDeliveryMethod(dto.getDeliveryMethod() != null ? dto.getDeliveryMethod() : 1);
        task.setDeliveryPoint(dto.getDeliveryPoint());
        task.setPriority(dto.getPriority() != null ? dto.getPriority() : 1);
        task.setStatus(TaskStatus.PENDING_PICKUP.getCode());
        task.setAssignTime(LocalDateTime.now());
        task.setDeleted(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        // 更新快递员负载
        bestCourier.setCurrentLoad(bestCourier.getCurrentLoad() + 1);
        if (bestCourier.getStatus() == 1) {
            bestCourier.setStatus(2); // 变为配送中
        }
        bestCourier.setUpdateTime(LocalDateTime.now());
        courierMapper.updateById(bestCourier);

        // 发送派单消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("taskNo", task.getTaskNo());
        msg.put("courierId", bestCourier.getId());
        msg.put("courierName", bestCourier.getName());
        // rocketMQ disabled

        log.info("智能派单完成: 任务={} 快递员={}", task.getTaskNo(), bestCourier.getName());
        return task;
    }

    @Override
    @Transactional
    public DeliveryTask manualAssign(Long taskId, Long courierId) {
        DeliveryTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        Courier courier = courierMapper.selectById(courierId);
        if (courier == null) {
            throw new RuntimeException("快递员不存在");
        }
        if (courier.getCurrentLoad() >= courier.getMaxLoad()) {
            throw new RuntimeException("快递员已满载");
        }

        task.setCourierId(courier.getId());
        task.setCourierName(courier.getName());
        task.setCourierPhone(courier.getPhone());
        task.setAssignTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        courier.setCurrentLoad(courier.getCurrentLoad() + 1);
        courier.setUpdateTime(LocalDateTime.now());
        courierMapper.updateById(courier);

        log.info("手动派单: 任务={} 快递员={}", task.getTaskNo(), courier.getName());
        return task;
    }

    @Override
    @Transactional
    public void pickup(Long taskId) {
        DeliveryTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (task.getStatus() != TaskStatus.PENDING_PICKUP.getCode()) {
            throw new RuntimeException("当前状态不允许取件");
        }
        task.setStatus(TaskStatus.DELIVERING.getCode());
        task.setPickupTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("快递员取件: {}", task.getTaskNo());
    }

    @Override
    @Transactional
    public void signoff(SignoffDTO dto) {
        DeliveryTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (task.getStatus() != TaskStatus.DELIVERING.getCode()) {
            throw new RuntimeException("当前状态不允许签收操作");
        }

        if (Boolean.TRUE.equals(dto.getAccepted())) {
            task.setStatus(TaskStatus.SIGNED.getCode());
            task.setSignTime(LocalDateTime.now());
            task.setSignName(dto.getSignName());
            task.setSignPhoto(dto.getSignPhoto());
        } else {
            task.setStatus(TaskStatus.REJECTED.getCode());
            task.setRemark(dto.getRemark());
        }
        task.setDeliveryTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        // 更新快递员负载和统计
        Courier courier = courierMapper.selectById(task.getCourierId());
        if (courier != null) {
            courier.setCurrentLoad(Math.max(0, courier.getCurrentLoad() - 1));
            courier.setTotalDeliveries(courier.getTotalDeliveries() + 1);
            courier.setTodayDeliveries(courier.getTodayDeliveries() + 1);
            if (courier.getCurrentLoad() == 0) {
                courier.setStatus(1); // 变为空闲
            }
            courier.setUpdateTime(LocalDateTime.now());
            courierMapper.updateById(courier);
        }

        // 发送签收消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("taskNo", task.getTaskNo());
        msg.put("waybillNo", task.getWaybillNo());
        msg.put("signed", dto.getAccepted());
        // rocketMQ disabled

        log.info("签收处理: 任务={} 签收={}", task.getTaskNo(), dto.getAccepted());
    }

    @Override
    public DeliveryTask getById(Long id) {
        DeliveryTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return task;
    }

    @Override
    public Page<DeliveryTask> pageTasks(Long courierId, Integer status, int page, int size) {
        LambdaQueryWrapper<DeliveryTask> wrapper = new LambdaQueryWrapper<>();
        if (courierId != null) {
            wrapper.eq(DeliveryTask::getCourierId, courierId);
        }
        if (status != null) {
            wrapper.eq(DeliveryTask::getStatus, status);
        }
        wrapper.orderByDesc(DeliveryTask::getCreateTime);
        return taskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void changeDeliveryMethod(Long taskId, Integer method, String deliveryPoint) {
        DeliveryTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (task.getStatus() >= TaskStatus.SIGNED.getCode()) {
            throw new RuntimeException("已完成的任务不能修改配送方式");
        }
        task.setDeliveryMethod(method);
        task.setDeliveryPoint(deliveryPoint);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("修改配送方式: 任务={} 方式={} 地点={}", task.getTaskNo(), method, deliveryPoint);
    }

    /**
     * 智能选择最优快递员
     * 评分公式: score = distanceScore * 0.4 + loadScore * 0.3 + priorityScore * 0.3
     */
    private Courier selectBestCourier(List<Courier> candidates, DispatchRequestDTO dto) {
        Map<Long, BigDecimal> scores = new HashMap<>();

        for (Courier courier : candidates) {
            BigDecimal distanceScore = calculateDistanceScore(courier, dto);
            BigDecimal loadScore = calculateLoadScore(courier);
            BigDecimal priorityScore = calculatePriorityScore(courier, dto);

            BigDecimal totalScore = distanceScore.multiply(BigDecimal.valueOf(0.4))
                    .add(loadScore.multiply(BigDecimal.valueOf(0.3)))
                    .add(priorityScore.multiply(BigDecimal.valueOf(0.3)));
            scores.put(courier.getId(), totalScore);
        }

        // 选择评分最高的快递员
        Long bestId = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new RuntimeException("无法选择快递员"));

        return candidates.stream()
                .filter(c -> c.getId().equals(bestId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 距离评分：距离越近分数越高（满分100）
     */
    private BigDecimal calculateDistanceScore(Courier courier, DispatchRequestDTO dto) {
        if (courier.getLongitude() == null || courier.getLatitude() == null
                || dto.getReceiverLongitude() == null || dto.getReceiverLatitude() == null) {
            return BigDecimal.valueOf(50); // 无位置信息给中间分
        }
        // 简化距离计算（欧几里得距离近似）
        double dLon = dto.getReceiverLongitude().subtract(courier.getLongitude()).doubleValue();
        double dLat = dto.getReceiverLatitude().subtract(courier.getLatitude()).doubleValue();
        double distance = Math.sqrt(dLon * dLon + dLat * dLat) * 111; // 粗略转换为km

        // 距离越小分数越高，5km以内满分，超过20km为0分
        if (distance <= 5) return BigDecimal.valueOf(100);
        if (distance >= 20) return BigDecimal.ZERO;
        return BigDecimal.valueOf(100 - (distance - 5) * (100.0 / 15)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 负载评分：负载越低分数越高（满分100）
     */
    private BigDecimal calculateLoadScore(Courier courier) {
        if (courier.getMaxLoad() == null || courier.getMaxLoad() == 0) {
            return BigDecimal.valueOf(50);
        }
        double loadRatio = (double) courier.getCurrentLoad() / courier.getMaxLoad();
        return BigDecimal.valueOf((1 - loadRatio) * 100).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 时效评分：加急件优先分配给评分高、配送快的快递员
     */
    private BigDecimal calculatePriorityScore(Courier courier, DispatchRequestDTO dto) {
        int priority = dto.getPriority() != null ? dto.getPriority() : 1;
        BigDecimal ratingScore = courier.getRating() != null
                ? courier.getRating().multiply(BigDecimal.valueOf(20)) // 5分制 * 20 = 满分100
                : BigDecimal.valueOf(50);

        if (priority >= 2) {
            // 加急件更看重快递员评分
            return ratingScore;
        }
        return BigDecimal.valueOf(70); // 普通件给固定分
    }

    private String generateTaskNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "DT" + date + snowflake.substring(snowflake.length() - 8);
    }
}
