package com.orange.logistics.transport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.logistics.transport.entity.TransportRoute;
import com.orange.logistics.transport.repository.TransportRouteMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 线路优化算法
 * - Dijkstra 最短路径
 * - 考虑实时路况权重
 * - 支持多中转站路径规划
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteOptimizer {

    private final TransportRouteMapper routeMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 路况权重缓存key前缀
     */
    private static final String TRAFFIC_WEIGHT_KEY = "route:traffic:weight:";

    /**
     * 默认路况权重（1.0 = 正常）
     */
    private static final double DEFAULT_TRAFFIC_WEIGHT = 1.0;

    /**
     * Dijkstra最短路径算法
     * 在所有可用线路中找到从起点到终点的最短路径
     *
     * @param originCity 起始城市
     * @param destCity   目的城市
     * @return 最优路径结果
     */
    @Operation(summary = "Dijkstra最短路径规划")
    public RouteResult findShortestPath(String originCity, String destCity) {
        if (originCity == null || destCity == null) {
            throw new IllegalArgumentException("起始城市和目的城市不能为空");
        }
        if (originCity.equals(destCity)) {
            throw new IllegalArgumentException("起始城市和目的城市不能相同");
        }

        long startTime = System.currentTimeMillis();

        // 加载所有启用的线路，构建图
        List<TransportRoute> allRoutes = routeMapper.selectList(
                new LambdaQueryWrapper<TransportRoute>().eq(TransportRoute::getStatus, 1));

        if (allRoutes.isEmpty()) {
            return RouteResult.fail("没有可用的运输线路");
        }

        // 构建邻接表
        Map<String, List<Edge>> graph = buildGraph(allRoutes);

        // 检查起点是否在图中
        if (!graph.containsKey(originCity)) {
            return RouteResult.fail("起始城市 [" + originCity + "] 没有可用线路");
        }

        // Dijkstra算法
        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Map<String, Long> previousRouteId = new HashMap<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::getDistance));
        Set<String> visited = new HashSet<>();

        // 初始化
        for (String city : graph.keySet()) {
            distances.put(city, Double.MAX_VALUE);
        }
        distances.put(originCity, 0.0);
        pq.offer(new NodeDistance(originCity, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            String currentCity = current.getCity();

            if (visited.contains(currentCity)) continue;
            visited.add(currentCity);

            if (currentCity.equals(destCity)) break;

            List<Edge> edges = graph.getOrDefault(currentCity, Collections.emptyList());
            for (Edge edge : edges) {
                if (visited.contains(edge.getDestCity())) continue;

                // 获取实时路况权重
                double trafficWeight = getTrafficWeight(edge.getRouteId());
                double weightedDistance = edge.getDistance() * trafficWeight;
                double newDist = distances.get(currentCity) + weightedDistance;

                if (newDist < distances.getOrDefault(edge.getDestCity(), Double.MAX_VALUE)) {
                    distances.put(edge.getDestCity(), newDist);
                    previous.put(edge.getDestCity(), currentCity);
                    previousRouteId.put(edge.getDestCity(), edge.getRouteId());
                    pq.offer(new NodeDistance(edge.getDestCity(), newDist));
                }
            }
        }

        // 检查是否找到路径
        if (!distances.containsKey(destCity) || distances.get(destCity) == Double.MAX_VALUE) {
            return RouteResult.fail("未找到从 [" + originCity + "] 到 [" + destCity + "] 的可达路径");
        }

        // 回溯路径
        List<String> path = new ArrayList<>();
        List<Long> routeIds = new ArrayList<>();
        String current = destCity;
        while (current != null) {
            path.add(current);
            if (previousRouteId.containsKey(current)) {
                routeIds.add(previousRouteId.get(current));
            }
            current = previous.get(current);
        }
        Collections.reverse(path);
        Collections.reverse(routeIds);

        // 计算总耗时
        int totalHours = 0;
        double totalDistance = distances.get(destCity);
        for (Long routeId : routeIds) {
            TransportRoute route = allRoutes.stream()
                    .filter(r -> r.getId().equals(routeId))
                    .findFirst().orElse(null);
            if (route != null && route.getEstimatedHours() != null) {
                totalHours += route.getEstimatedHours();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        RouteResult result = RouteResult.builder()
                .success(true)
                .originCity(originCity)
                .destCity(destCity)
                .path(path)
                .routeIds(routeIds)
                .totalDistance(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP))
                .estimatedHours(totalHours)
                .hops(path.size() - 1)
                .algorithm("DIJKSTRA")
                .computeTimeMs(elapsed)
                .build();

        log.info("路径规划完成: {} -> {}, 路径={}, 距离={}km, 耗时={}h",
                originCity, destCity, path, totalDistance, totalHours);

        return result;
    }

    /**
     * 考虑实时路况的路径规划
     * 路况权重：1.0=畅通, 1.5=缓行, 2.0=拥堵, 3.0=严重拥堵
     *
     * @param originCity 起始城市
     * @param destCity   目的城市
     * @return 考虑路况的最优路径
     */
    @Operation(summary = "实时路况路径规划")
    public RouteResult findOptimalPathWithTraffic(String originCity, String destCity) {
        // 使用Dijkstra算法，但边权重乘以路况系数
        return findShortestPath(originCity, destCity);
    }

    /**
     * 更新线路实时路况权重
     *
     * @param routeId       线路ID
     * @param trafficWeight 路况权重 (1.0-3.0)
     */
    @Operation(summary = "更新路况权重")
    public void updateTrafficWeight(Long routeId, double trafficWeight) {
        if (trafficWeight < 0.5 || trafficWeight > 5.0) {
            throw new IllegalArgumentException("路况权重必须在0.5-5.0之间");
        }
        String key = TRAFFIC_WEIGHT_KEY + routeId;
        redisTemplate.opsForValue().set(key, String.valueOf(trafficWeight));
        log.info("更新路况权重: routeId={}, weight={}", routeId, trafficWeight);
    }

    /**
     * 查找所有可达路径（DFS，限制最大跳数）
     *
     * @param originCity 起始城市
     * @param destCity   目的城市
     * @param maxHops    最大中转次数
     * @return 所有可达路径
     */
    @Operation(summary = "查找所有可达路径")
    public List<RouteResult> findAllPaths(String originCity, String destCity, int maxHops) {
        List<TransportRoute> allRoutes = routeMapper.selectList(
                new LambdaQueryWrapper<TransportRoute>().eq(TransportRoute::getStatus, 1));

        Map<String, List<Edge>> graph = buildGraph(allRoutes);
        List<RouteResult> results = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        List<Long> currentRouteIds = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        currentPath.add(originCity);
        visited.add(originCity);

        dfs(graph, originCity, destCity, maxHops, currentPath, currentRouteIds, visited, results, allRoutes);

        // 按距离排序
        results.sort(Comparator.comparing(RouteResult::getTotalDistance));

        log.info("找到 {} -> {} 共 {} 条可达路径", originCity, destCity, results.size());
        return results;
    }

    // ========== 私有方法 ==========
    private Map<String, List<Edge>> buildGraph(List<TransportRoute> routes) {
        Map<String, List<Edge>> graph = new HashMap<>();
        for (TransportRoute route : routes) {
            graph.computeIfAbsent(route.getOriginCity(), k -> new ArrayList<>())
                    .add(new Edge(route.getDestCity(), route.getId(),
                            route.getDistance() != null ? route.getDistance().doubleValue() : 100));
            // 双向线路
            graph.computeIfAbsent(route.getDestCity(), k -> new ArrayList<>())
                    .add(new Edge(route.getOriginCity(), route.getId(),
                            route.getDistance() != null ? route.getDistance().doubleValue() : 100));
        }
        return graph;
    }

    private double getTrafficWeight(Long routeId) {
        String key = TRAFFIC_WEIGHT_KEY + routeId;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return DEFAULT_TRAFFIC_WEIGHT;
            }
        }
        return DEFAULT_TRAFFIC_WEIGHT;
    }

    private void dfs(Map<String, List<Edge>> graph, String current, String dest, int maxHops,
                     List<String> path, List<Long> routeIds, Set<String> visited,
                     List<RouteResult> results, List<TransportRoute> allRoutes) {
        if (current.equals(dest)) {
            // 找到一条路径
            double totalDist = 0;
            int totalHours = 0;
            for (Long routeId : routeIds) {
                TransportRoute route = allRoutes.stream()
                        .filter(r -> r.getId().equals(routeId)).findFirst().orElse(null);
                if (route != null) {
                    totalDist += route.getDistance() != null ? route.getDistance().doubleValue() : 0;
                    totalHours += route.getEstimatedHours() != null ? route.getEstimatedHours() : 0;
                }
            }
            results.add(RouteResult.builder()
                    .success(true)
                    .originCity(path.get(0))
                    .destCity(dest)
                    .path(new ArrayList<>(path))
                    .routeIds(new ArrayList<>(routeIds))
                    .totalDistance(BigDecimal.valueOf(totalDist).setScale(2, RoundingMode.HALF_UP))
                    .estimatedHours(totalHours)
                    .hops(path.size() - 1)
                    .algorithm("DFS")
                    .build());
            return;
        }

        if (path.size() - 1 >= maxHops) return;

        List<Edge> edges = graph.getOrDefault(current, Collections.emptyList());
        for (Edge edge : edges) {
            if (visited.contains(edge.getDestCity())) continue;
            visited.add(edge.getDestCity());
            path.add(edge.getDestCity());
            routeIds.add(edge.getRouteId());

            dfs(graph, edge.getDestCity(), dest, maxHops, path, routeIds, visited, results, allRoutes);

            path.remove(path.size() - 1);
            routeIds.remove(routeIds.size() - 1);
            visited.remove(edge.getDestCity());
        }
    }

    // ========== 内部类 ==========

    @Data
    @AllArgsConstructor
    private static class Edge {
        private String destCity;
        private Long routeId;
        private double distance;
    }

    @Data
    @AllArgsConstructor
    private static class NodeDistance {
        private String city;
        private double distance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteResult {
        private boolean success;
        private String message;
        private String originCity;
        private String destCity;
        private List<String> path;        // 途经城市列表
        private List<Long> routeIds;      // 使用的线路ID列表
        private BigDecimal totalDistance;  // 总距离(km)
        private Integer estimatedHours;   // 预计耗时(小时)
        private Integer hops;             // 中转次数
        private String algorithm;         // 使用的算法
        private Long computeTimeMs;       // 计算耗时(ms)

        public static RouteResult fail(String message) {
            return RouteResult.builder().success(false).message(message).build();
        }
    }
}
