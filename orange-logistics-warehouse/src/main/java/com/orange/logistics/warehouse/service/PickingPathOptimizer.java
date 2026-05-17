package com.orange.logistics.warehouse.service;

import com.orange.logistics.warehouse.entity.WarehouseLocation;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 拣货路径优化器
 * 基于货架坐标的最短路径算法（贪心 + A*）
 * 输入：拣货单（多个SKU+库位），输出：最优拣货顺序
 */
@Slf4j
@Service
public class PickingPathOptimizer {

    /**
     * 优化拣货路径（贪心最近邻算法）
     * 从起始点出发，每次选择距离最近的未访问库位
     *
     * @param startPoint 起始点坐标（通常是拣货区入口）
     * @param pickItems  待拣货项列表
     * @return 优化后的拣货路径
     */
    @Operation(summary = "优化拣货路径（贪心算法）")
    public PickingRoute optimizeByGreedy(Coordinate startPoint, List<PickItem> pickItems) {
        if (pickItems == null || pickItems.isEmpty()) {
            throw new IllegalArgumentException("拣货列表不能为空");
        }
        if (startPoint == null) {
            startPoint = new Coordinate(0, 0, 0); // 默认起始点
        }

        long startTime = System.currentTimeMillis();

        // 贪心最近邻算法
        List<PickItem> optimizedOrder = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Coordinate current = startPoint;
        double totalDistance = 0;

        while (visited.size() < pickItems.size()) {
            double minDist = Double.MAX_VALUE;
            int nearestIdx = -1;

            for (int i = 0; i < pickItems.size(); i++) {
                if (visited.contains(i)) continue;

                double dist = calculateDistance(current, pickItems.get(i).getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearestIdx = i;
                }
            }

            if (nearestIdx >= 0) {
                visited.add(nearestIdx);
                PickItem item = pickItems.get(nearestIdx);
                item.setSequence(optimizedOrder.size() + 1);
                item.setDistanceFromPrev(minDist);
                optimizedOrder.add(item);
                totalDistance += minDist;
                current = item.getLocation();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        PickingRoute route = PickingRoute.builder()
                .items(optimizedOrder)
                .totalDistance(totalDistance)
                .estimatedTime(estimatePickingTime(optimizedOrder, totalDistance))
                .algorithm("GREEDY_NEAREST_NEIGHBOR")
                .optimizationTimeMs(elapsed)
                .build();

        log.info("拣货路径优化完成(贪心): {}个库位, 总距离={:.1f}m, 预计耗时={}min",
                pickItems.size(), totalDistance, route.getEstimatedTime());

        return route;
    }

    /**
     * 优化拣货路径（A*算法 - 考虑通道约束）
     * 适用于有通道限制的仓库布局
     *
     * @param startPoint   起始点
     * @param pickItems    待拣货项
     * @param warehouseMap 仓库地图（通道信息）
     * @return 优化后的拣货路径
     */
    @Operation(summary = "优化拣货路径（A*算法）")
    public PickingRoute optimizeByAStar(Coordinate startPoint, List<PickItem> pickItems,
                                        WarehouseMap warehouseMap) {
        if (pickItems == null || pickItems.isEmpty()) {
            throw new IllegalArgumentException("拣货列表不能为空");
        }
        if (startPoint == null) {
            startPoint = new Coordinate(0, 0, 0);
        }

        long startTime = System.currentTimeMillis();

        // 按通道分组，减少跨通道移动
        Map<String, List<PickItem>> aisleGroups = pickItems.stream()
                .collect(Collectors.groupingBy(item -> extractAisle(item.getLocationCode())));

        // 对通道排序（S型路径：奇数通道正向，偶数通道反向）
        List<String> sortedAisles = new ArrayList<>(aisleGroups.keySet());
        sortedAisles.sort(Comparator.naturalOrder());

        List<PickItem> optimizedOrder = new ArrayList<>();
        Coordinate current = startPoint;
        double totalDistance = 0;
        int sequence = 1;

        for (int aisleIdx = 0; aisleIdx < sortedAisles.size(); aisleIdx++) {
            String aisle = sortedAisles.get(aisleIdx);
            List<PickItem> aisleItems = aisleGroups.get(aisle);

            // S型遍历：奇数通道按层从低到高，偶数通道从高到低
            boolean ascending = (aisleIdx % 2 == 0);
            aisleItems.sort((a, b) -> {
                int cmp = Integer.compare(a.getLocation().getY(), b.getLocation().getY());
                return ascending ? cmp : -cmp;
            });

            for (PickItem item : aisleItems) {
                double dist = calculateDistance(current, item.getLocation());

                // A*启发式：如果有障碍物，绕行距离增加
                if (warehouseMap != null && warehouseMap.hasObstacle(current, item.getLocation())) {
                    dist *= 1.5; // 绕行系数
                }

                item.setSequence(sequence++);
                item.setDistanceFromPrev(dist);
                optimizedOrder.add(item);
                totalDistance += dist;
                current = item.getLocation();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        PickingRoute route = PickingRoute.builder()
                .items(optimizedOrder)
                .totalDistance(totalDistance)
                .estimatedTime(estimatePickingTime(optimizedOrder, totalDistance))
                .algorithm("A_STAR_S_SHAPE")
                .optimizationTimeMs(elapsed)
                .build();

        log.info("拣货路径优化完成(A*): {}个库位, 总距离={:.1f}m, 预计耗时={}min",
                pickItems.size(), totalDistance, route.getEstimatedTime());

        return route;
    }

    /**
     * 从库位编码解析坐标
     * 库位编码格式: A-01-02-03 (区域-通道-货架-层)
     */
    @Operation(summary = "解析库位坐标")
    public Coordinate parseLocationCode(String locationCode) {
        if (locationCode == null || locationCode.isBlank()) {
            return new Coordinate(0, 0, 0);
        }

        String[] parts = locationCode.split("-");
        if (parts.length < 4) {
            return new Coordinate(0, 0, 0);
        }

        try {
            // 区域转换为X偏移（A=0, B=10, C=20...）
            int zoneOffset = (parts[0].charAt(0) - 'A') * 10;
            int aisle = Integer.parseInt(parts[1]);  // X坐标
            int shelf = Integer.parseInt(parts[2]);  // Y坐标
            int layer = Integer.parseInt(parts[3]);  // Z坐标

            return new Coordinate(zoneOffset + aisle, shelf, layer);
        } catch (NumberFormatException e) {
            log.warn("库位编码解析失败: {}", locationCode);
            return new Coordinate(0, 0, 0);
        }
    }

    // ========== 私有方法 ==========

    /**
     * 计算两点间的欧几里得距离（3D）
     * 考虑层高差异（垂直移动比水平移动慢）
     */
    private double calculateDistance(Coordinate a, Coordinate b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = (a.getZ() - b.getZ()) * 2.0; // 垂直距离权重加倍（爬梯/升降机）
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 从库位编码提取通道号
     */
    private String extractAisle(String locationCode) {
        if (locationCode == null) return "00";
        String[] parts = locationCode.split("-");
        return parts.length >= 2 ? parts[0] + "-" + parts[1] : "00";
    }

    /**
     * 估算拣货时间（分钟）
     * 移动速度1m/s，每个SKU拣货15秒
     */
    private int estimatePickingTime(List<PickItem> items, double totalDistance) {
        double moveTime = totalDistance / 60.0; // 分钟（1m/s = 60m/min）
        double pickTime = items.size() * 0.25; // 每个SKU 15秒 = 0.25分钟
        return (int) Math.ceil(moveTime + pickTime);
    }

    // ========== 内部类 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate {
        private int x; // 通道号
        private int y; // 货架位置
        private int z; // 层号
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickItem {
        private String skuCode;
        private String skuName;
        private Integer quantity;
        private String locationCode;     // 库位编码
        private Coordinate location;     // 库位坐标
        private Integer sequence;        // 拣货顺序（优化后）
        private Double distanceFromPrev; // 距上一个库位的距离
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickingRoute {
        private List<PickItem> items;          // 优化后的拣货顺序
        private Double totalDistance;          // 总移动距离
        private Integer estimatedTime;         // 预计耗时(分钟)
        private String algorithm;              // 使用的算法
        private Long optimizationTimeMs;       // 优化计算耗时(毫秒)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseMap {
        private int width;
        private int height;
        private Set<String> obstacles; // 障碍物坐标集合 "x,y"

        public boolean hasObstacle(Coordinate from, Coordinate to) {
            if (obstacles == null || obstacles.isEmpty()) return false;
            // 简化检查：检查两点之间的中点是否有障碍物
            int midX = (from.getX() + to.getX()) / 2;
            int midY = (from.getY() + to.getY()) / 2;
            return obstacles.contains(midX + "," + midY);
        }
    }
}
