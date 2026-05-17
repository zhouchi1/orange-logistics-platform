package com.orange.logistics.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 配送路径优化器
 * 基于贪心算法（最近邻居法）求解TSP问题
 * 为快递员规划最优配送顺序
 */
@Slf4j
@Component
public class DeliveryRouteOptimizer {

    // 骑行平均速度 km/h
    private static final double AVG_SPEED_KMH = 15.0;
    // 每个配送点平均停留时间（分钟）
    private static final double STOP_TIME_MINUTES = 5.0;

    /**
     * 优化配送路径（最近邻居贪心算法）
     *
     * @param startLat       快递员起始纬度
     * @param startLng       快递员起始经度
     * @param deliveryPoints 配送点列表，每个包含{id, latitude, longitude, address, deadline}
     * @return 优化后的配送顺序 + 预计时间
     */
    public Map<String, Object> optimizeRoute(double startLat, double startLng,
                                              List<Map<String, Object>> deliveryPoints) {
        if (deliveryPoints == null || deliveryPoints.isEmpty()) {
            return Map.of("route", Collections.emptyList(), "totalDistance", 0, "totalTime", 0);
        }

        int n = deliveryPoints.size();
        boolean[] visited = new boolean[n];
        List<Map<String, Object>> optimizedRoute = new ArrayList<>();

        double currentLat = startLat;
        double currentLng = startLng;
        double totalDistance = 0;

        // 贪心：每次选择最近的未访问点
        for (int step = 0; step < n; step++) {
            int nearest = -1;
            double minDist = Double.MAX_VALUE;

            for (int i = 0; i < n; i++) {
                if (!visited[i]) {
                    double lat = toDouble(deliveryPoints.get(i).get("latitude"));
                    double lng = toDouble(deliveryPoints.get(i).get("longitude"));
                    double dist = haversineDistance(currentLat, currentLng, lat, lng);

                    // 考虑时效约束：如果有截止时间快到的，优先处理
                    if (deliveryPoints.get(i).containsKey("deadline")) {
                        int urgencyBonus = getUrgencyBonus(deliveryPoints.get(i));
                        dist = dist * (1.0 - urgencyBonus * 0.01); // 紧急的"距离"打折
                    }

                    if (dist < minDist) {
                        minDist = dist;
                        nearest = i;
                    }
                }
            }

            if (nearest >= 0) {
                visited[nearest] = true;
                Map<String, Object> point = deliveryPoints.get(nearest);
                double lat = toDouble(point.get("latitude"));
                double lng = toDouble(point.get("longitude"));
                double actualDist = haversineDistance(currentLat, currentLng, lat, lng);
                totalDistance += actualDist;

                Map<String, Object> routePoint = new LinkedHashMap<>();
                routePoint.put("sequence", step + 1);
                routePoint.put("pointId", point.get("id"));
                routePoint.put("address", point.get("address"));
                routePoint.put("latitude", lat);
                routePoint.put("longitude", lng);
                routePoint.put("distanceFromPrev", Math.round(actualDist * 100.0) / 100.0);
                routePoint.put("cumulativeDistance", Math.round(totalDistance * 100.0) / 100.0);
                routePoint.put("estimatedArrival", estimateArrivalMinutes(totalDistance, step + 1));
                optimizedRoute.add(routePoint);

                currentLat = lat;
                currentLng = lng;
            }
        }

        // 计算总时间
        int totalMinutes = estimateArrivalMinutes(totalDistance, n);

        // 2-opt 局部优化（简化版）
        optimizedRoute = twoOptImprove(startLat, startLng, optimizedRoute, deliveryPoints);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startPoint", Map.of("latitude", startLat, "longitude", startLng));
        result.put("totalPoints", n);
        result.put("totalDistanceKm", Math.round(totalDistance * 100.0) / 100.0);
        result.put("totalTimeMinutes", totalMinutes);
        result.put("avgDistanceBetweenStops", Math.round(totalDistance / n * 100.0) / 100.0);
        result.put("route", optimizedRoute);

        log.info("路径优化完成: {}个配送点 总距离={}km 预计耗时={}min",
                n, Math.round(totalDistance * 100.0) / 100.0, totalMinutes);

        return result;
    }

    /**
     * 2-opt 局部优化：尝试交换路径中的两段，看是否能缩短总距离
     */
    private List<Map<String, Object>> twoOptImprove(double startLat, double startLng,
                                                     List<Map<String, Object>> route,
                                                     List<Map<String, Object>> originalPoints) {
        if (route.size() <= 3) return route; // 太少不值得优化

        boolean improved = true;
        int maxIterations = 100;
        int iteration = 0;

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (int i = 0; i < route.size() - 1; i++) {
                for (int j = i + 2; j < route.size(); j++) {
                    double currentDist = segmentDistance(route, i, j, startLat, startLng);
                    double newDist = reversedSegmentDistance(route, i, j, startLat, startLng);

                    if (newDist < currentDist - 0.01) { // 有改善
                        // 反转 i+1 到 j 之间的路径
                        Collections.reverse(route.subList(i + 1, j + 1));
                        improved = true;
                    }
                }
            }
        }

        // 重新计算序号和累计距离
        double cumDist = 0;
        double prevLat = startLat, prevLng = startLng;
        for (int i = 0; i < route.size(); i++) {
            Map<String, Object> point = route.get(i);
            double lat = toDouble(point.get("latitude"));
            double lng = toDouble(point.get("longitude"));
            double dist = haversineDistance(prevLat, prevLng, lat, lng);
            cumDist += dist;

            point.put("sequence", i + 1);
            point.put("distanceFromPrev", Math.round(dist * 100.0) / 100.0);
            point.put("cumulativeDistance", Math.round(cumDist * 100.0) / 100.0);
            point.put("estimatedArrival", estimateArrivalMinutes(cumDist, i + 1));

            prevLat = lat;
            prevLng = lng;
        }

        return route;
    }

    private double segmentDistance(List<Map<String, Object>> route, int i, int j,
                                    double startLat, double startLng) {
        double dist = 0;
        double prevLat = (i == 0) ? startLat : toDouble(route.get(i - 1).get("latitude"));
        double prevLng = (i == 0) ? startLng : toDouble(route.get(i - 1).get("longitude"));

        for (int k = i; k <= j; k++) {
            double lat = toDouble(route.get(k).get("latitude"));
            double lng = toDouble(route.get(k).get("longitude"));
            dist += haversineDistance(prevLat, prevLng, lat, lng);
            prevLat = lat;
            prevLng = lng;
        }
        return dist;
    }

    private double reversedSegmentDistance(List<Map<String, Object>> route, int i, int j,
                                            double startLat, double startLng) {
        double dist = 0;
        double prevLat = (i == 0) ? startLat : toDouble(route.get(i - 1).get("latitude"));
        double prevLng = (i == 0) ? startLng : toDouble(route.get(i - 1).get("longitude"));

        for (int k = j; k >= i; k--) {
            double lat = toDouble(route.get(k).get("latitude"));
            double lng = toDouble(route.get(k).get("longitude"));
            dist += haversineDistance(prevLat, prevLng, lat, lng);
            prevLat = lat;
            prevLng = lng;
        }
        return dist;
    }

    private int getUrgencyBonus(Map<String, Object> point) {
        Object deadline = point.get("deadline");
        if (deadline == null) return 0;
        // 简化：deadline 越近 bonus 越高
        return 30; // 有截止时间的给30%优先级
    }

    private int estimateArrivalMinutes(double distanceKm, int stops) {
        double travelMinutes = (distanceKm / AVG_SPEED_KMH) * 60;
        double stopMinutes = stops * STOP_TIME_MINUTES;
        return (int) Math.ceil(travelMinutes + stopMinutes);
    }

    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double toDouble(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0; }
    }
}
