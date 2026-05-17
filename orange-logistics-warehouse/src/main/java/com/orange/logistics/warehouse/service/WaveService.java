package com.orange.logistics.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.warehouse.entity.PickWave;
import com.orange.logistics.warehouse.repository.WarehouseMapper;
import cn.hutool.core.util.IdUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 波次管理服务
 * - 按时间窗口聚合订单
 * - 按配送区域分组
 * - 生成波次拣货任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaveService {

    private final WarehouseMapper warehouseMapper;

    /**
     * 默认波次时间窗口（分钟）
     */
    private static final int DEFAULT_TIME_WINDOW_MINUTES = 30;

    /**
     * 单波次最大订单数
     */
    private static final int MAX_ORDERS_PER_WAVE = 50;

    /**
     * 单波次最大SKU数
     */
    private static final int MAX_ITEMS_PER_WAVE = 200;

    /**
     * 创建波次（按时间窗口聚合）
     * 将指定时间窗口内的订单聚合为一个波次
     *
     * @param warehouseId       仓库ID
     * @param orders            待处理订单列表
     * @param timeWindowMinutes 时间窗口（分钟）
     * @return 生成的波次列表
     */
    @Operation(summary = "按时间窗口创建波次")
    @Transactional
    public List<WaveResult> createWavesByTimeWindow(Long warehouseId, List<WaveOrder> orders,
                                                     Integer timeWindowMinutes) {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("订单列表不能为空");
        }

        int window = timeWindowMinutes != null ? timeWindowMinutes : DEFAULT_TIME_WINDOW_MINUTES;

        // 按订单创建时间排序
        orders.sort(Comparator.comparing(WaveOrder::getCreateTime));

        List<WaveResult> waves = new ArrayList<>();
        List<WaveOrder> currentBatch = new ArrayList<>();
        LocalDateTime windowStart = orders.get(0).getCreateTime();

        for (WaveOrder order : orders) {
            // 检查是否超出时间窗口或超出最大订单数
            boolean timeExceeded = order.getCreateTime().isAfter(windowStart.plusMinutes(window));
            boolean countExceeded = currentBatch.size() >= MAX_ORDERS_PER_WAVE;
            int currentItems = currentBatch.stream().mapToInt(WaveOrder::getItemCount).sum();
            boolean itemsExceeded = currentItems + order.getItemCount() > MAX_ITEMS_PER_WAVE;

            if ((timeExceeded || countExceeded || itemsExceeded) && !currentBatch.isEmpty()) {
                // 生成当前波次
                WaveResult wave = buildWave(warehouseId, currentBatch);
                waves.add(wave);
                currentBatch = new ArrayList<>();
                windowStart = order.getCreateTime();
            }

            currentBatch.add(order);
        }

        // 处理最后一批
        if (!currentBatch.isEmpty()) {
            WaveResult wave = buildWave(warehouseId, currentBatch);
            waves.add(wave);
        }

        log.info("按时间窗口创建波次: 仓库={}, 订单数={}, 生成波次数={}, 时间窗口={}min",
                warehouseId, orders.size(), waves.size(), window);

        return waves;
    }

    /**
     * 创建波次（按配送区域分组）
     * 将相同配送区域的订单聚合为一个波次，便于集中配送
     *
     * @param warehouseId 仓库ID
     * @param orders      待处理订单列表
     * @return 按区域分组的波次列表
     */
    @Operation(summary = "按配送区域创建波次")
    @Transactional
    public List<WaveResult> createWavesByRegion(Long warehouseId, List<WaveOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("订单列表不能为空");
        }

        // 按配送区域分组
        Map<String, List<WaveOrder>> regionGroups = orders.stream()
                .collect(Collectors.groupingBy(order ->
                        normalizeRegion(order.getReceiverDistrict(), order.getReceiverCity())));

        List<WaveResult> waves = new ArrayList<>();

        for (Map.Entry<String, List<WaveOrder>> entry : regionGroups.entrySet()) {
            String region = entry.getKey();
            List<WaveOrder> regionOrders = entry.getValue();

            // 如果区域订单数超过最大值，再按数量拆分
            List<List<WaveOrder>> batches = splitIntoBatches(regionOrders, MAX_ORDERS_PER_WAVE);

            for (List<WaveOrder> batch : batches) {
                WaveResult wave = buildWave(warehouseId, batch);
                wave.setRegion(region);
                waves.add(wave);
            }
        }

        log.info("按区域创建波次: 仓库={}, 订单数={}, 区域数={}, 生成波次数={}",
                warehouseId, orders.size(), regionGroups.size(), waves.size());

        return waves;
    }

    /**
     * 智能波次创建（综合时间+区域+优先级）
     *
     * @param warehouseId 仓库ID
     * @param orders      待处理订单列表
     * @return 优化后的波次列表
     */
    @Operation(summary = "智能创建波次")
    @Transactional
    public List<WaveResult> createSmartWaves(Long warehouseId, List<WaveOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("订单列表不能为空");
        }

        // 1. 先按优先级分组（加急件优先处理）
        Map<Integer, List<WaveOrder>> priorityGroups = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getPriority() != null ? o.getPriority() : 1));

        List<WaveResult> allWaves = new ArrayList<>();

        // 2. 高优先级订单单独成波（当日达、加急）
        List<WaveOrder> urgentOrders = new ArrayList<>();
        urgentOrders.addAll(priorityGroups.getOrDefault(3, Collections.emptyList())); // 当日达
        urgentOrders.addAll(priorityGroups.getOrDefault(2, Collections.emptyList())); // 加急
        if (!urgentOrders.isEmpty()) {
            List<WaveResult> urgentWaves = createWavesByRegion(warehouseId, urgentOrders);
            urgentWaves.forEach(w -> w.setPriority(2)); // 标记为高优先级波次
            allWaves.addAll(urgentWaves);
        }

        // 3. 普通订单按区域+时间窗口组合
        List<WaveOrder> normalOrders = priorityGroups.getOrDefault(1, Collections.emptyList());
        if (!normalOrders.isEmpty()) {
            // 先按区域分组
            Map<String, List<WaveOrder>> regionGroups = normalOrders.stream()
                    .collect(Collectors.groupingBy(order ->
                            normalizeRegion(order.getReceiverDistrict(), order.getReceiverCity())));

            for (Map.Entry<String, List<WaveOrder>> entry : regionGroups.entrySet()) {
                List<WaveOrder> regionOrders = entry.getValue();
                // 区域内再按时间窗口分组
                List<WaveResult> regionWaves = createWavesByTimeWindow(warehouseId, regionOrders, DEFAULT_TIME_WINDOW_MINUTES);
                regionWaves.forEach(w -> {
                    w.setRegion(entry.getKey());
                    w.setPriority(1);
                });
                allWaves.addAll(regionWaves);
            }
        }

        // 4. 按优先级排序
        allWaves.sort(Comparator.comparingInt(WaveResult::getPriority).reversed());

        log.info("智能波次创建完成: 仓库={}, 总订单数={}, 加急={}, 普通={}, 波次数={}",
                warehouseId, orders.size(), urgentOrders.size(), normalOrders.size(), allWaves.size());

        return allWaves;
    }

    /**
     * 生成波次拣货任务
     * 将波次中的订单转换为拣货任务列表
     *
     * @param wave 波次信息
     * @return 拣货任务列表
     */
    @Operation(summary = "生成波次拣货任务")
    public List<PickingPathOptimizer.PickItem> generatePickingTasks(WaveResult wave) {
        if (wave == null || wave.getOrders() == null) {
            throw new IllegalArgumentException("波次信息不能为空");
        }

        // 合并相同SKU的拣货数量
        Map<String, PickingPathOptimizer.PickItem> mergedItems = new LinkedHashMap<>();

        for (WaveOrder order : wave.getOrders()) {
            if (order.getItems() == null) continue;

            for (WaveOrderItem item : order.getItems()) {
                mergedItems.merge(item.getSkuCode(),
                        PickingPathOptimizer.PickItem.builder()
                                .skuCode(item.getSkuCode())
                                .skuName(item.getSkuName())
                                .quantity(item.getQuantity())
                                .locationCode(item.getLocationCode())
                                .build(),
                        (existing, newItem) -> {
                            existing.setQuantity(existing.getQuantity() + newItem.getQuantity());
                            return existing;
                        });
            }
        }

        List<PickingPathOptimizer.PickItem> pickItems = new ArrayList<>(mergedItems.values());
        log.info("波次 {} 生成拣货任务: {} 个SKU", wave.getWaveNo(), pickItems.size());
        return pickItems;
    }

    // ========== 私有方法 ==========
    private WaveResult buildWave(Long warehouseId, List<WaveOrder> orders) {
        String waveNo = generateWaveNo();
        int totalItems = orders.stream().mapToInt(WaveOrder::getItemCount).sum();

        return WaveResult.builder()
                .waveNo(waveNo)
                .warehouseId(warehouseId)
                .orders(orders)
                .orderCount(orders.size())
                .totalItems(totalItems)
                .status(0) // 待执行
                .priority(1)
                .createTime(LocalDateTime.now())
                .build();
    }

    private String generateWaveNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String snowflake = String.valueOf(IdUtil.getSnowflakeNextId());
        return "WV" + date + snowflake.substring(snowflake.length() - 6);
    }

    private String normalizeRegion(String district, String city) {
        if (district != null && !district.isBlank()) {
            return district;
        }
        if (city != null && !city.isBlank()) {
            return city;
        }
        return "未知区域";
    }

    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    // ========== 内部类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaveOrder {
        private Long orderId;
        private String orderNo;
        private String receiverCity;
        private String receiverDistrict;
        private Integer itemCount;
        private Integer priority; // 1-普通 2-加急 3-当日达
        private LocalDateTime createTime;
        private List<WaveOrderItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaveOrderItem {
        private String skuCode;
        private String skuName;
        private Integer quantity;
        private String locationCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaveResult {
        private String waveNo;
        private Long warehouseId;
        private String region;
        private Integer priority;
        private Integer status; // 0-待执行 1-执行中 2-已完成
        private Integer orderCount;
        private Integer totalItems;
        private List<WaveOrder> orders;
        private LocalDateTime createTime;
    }
}
