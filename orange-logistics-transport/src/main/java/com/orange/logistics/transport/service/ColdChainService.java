package com.orange.logistics.transport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 冷链温控监控服务
 * 实时监控运输车辆温度，超限自动告警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColdChainService {

    private final StringRedisTemplate redisTemplate;

    // 温度阈值配置（可从 Nacos 动态获取）
    private static final double DEFAULT_MIN_TEMP = -25.0; // 冷冻最低温度
    private static final double DEFAULT_MAX_TEMP = -18.0; // 冷冻最高温度
    private static final double REFRIGERATED_MIN = 0.0;   // 冷藏最低温度
    private static final double REFRIGERATED_MAX = 8.0;   // 冷藏最高温度

    // 告警记录（生产环境应持久化）
    private final Map<String, List<Map<String, Object>>> alertHistory = new ConcurrentHashMap<>();

    /**
     * 上报温度数据
     *
     * @param vehicleId   车辆ID
     * @param compartment 货舱编号（一辆车可能有多个温区）
     * @param temperature 当前温度（℃）
     * @param humidity    湿度（%）
     * @param coldType    冷链类型：FROZEN（冷冻）、REFRIGERATED（冷藏）
     */
    public Map<String, Object> reportTemperature(String vehicleId, String compartment,
                                                  double temperature, double humidity,
                                                  String coldType) {
        String key = "coldchain:" + vehicleId + ":" + compartment;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 存储Redis（时序数据，保留24小时）
        String dataPoint = String.format("%.1f|%.1f|%s", temperature, humidity, timestamp);
        redisTemplate.opsForList().rightPush(key, dataPoint);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);

        // 只保留最近1440条（每分钟一条，24小时）
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 1440) {
            redisTemplate.opsForList().leftPop(key);
        }

        // 更新最新温控状态
        String latestKey = "coldchain:latest:" + vehicleId + ":" + compartment;
        redisTemplate.opsForHash().put(latestKey, "temperature", String.valueOf(temperature));
        redisTemplate.opsForHash().put(latestKey, "humidity", String.valueOf(humidity));
        redisTemplate.opsForHash().put(latestKey, "timestamp", timestamp);
        redisTemplate.opsForHash().put(latestKey, "coldType", coldType);

        // 检查是否超限
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleId", vehicleId);
        result.put("compartment", compartment);
        result.put("temperature", temperature);
        result.put("humidity", humidity);
        result.put("timestamp", timestamp);

        boolean isAlert = checkTemperatureAlert(vehicleId, compartment, temperature, coldType);
        result.put("alert", isAlert);

        if (isAlert) {
            String alertMsg = generateAlertMessage(vehicleId, compartment, temperature, coldType);
            result.put("alertMessage", alertMsg);
            recordAlert(vehicleId, compartment, temperature, coldType, alertMsg);
            log.warn("冷链温度告警: 车辆={} 货舱={} 温度={} 类型={}",
                    vehicleId, compartment, temperature, coldType);
        }

        return result;
    }

    /**
     * 查询温度曲线（最近N小时）
     */
    public Map<String, Object> getTemperatureCurve(String vehicleId, String compartment, int hours) {
        String key = "coldchain:" + vehicleId + ":" + compartment;
        int count = hours * 60; // 每分钟一条
        List<String> dataPoints = redisTemplate.opsForList().range(key, -count, -1);
        if (dataPoints == null || dataPoints.isEmpty()) {
            return Map.of("vehicleId", vehicleId, "data", Collections.emptyList());
        }

        List<Map<String, Object>> curve = new ArrayList<>();
        double maxTemp = Double.MIN_VALUE;
        double minTemp = Double.MAX_VALUE;
        double sumTemp = 0;

        for (String point : dataPoints) {
            String[] parts = point.split("\\|");
            if (parts.length >= 3) {
                double temp = Double.parseDouble(parts[0]);
                double hum = Double.parseDouble(parts[1]);
                String time = parts[2];

                maxTemp = Math.max(maxTemp, temp);
                minTemp = Math.min(minTemp, temp);
                sumTemp += temp;

                Map<String, Object> dp = new LinkedHashMap<>();
                dp.put("temperature", temp);
                dp.put("humidity", hum);
                dp.put("time", time);
                curve.add(dp);
            }
        }

        double avgTemp = curve.isEmpty() ? 0 : sumTemp / curve.size();

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("vehicleId", vehicleId);
        resultMap.put("compartment", compartment);
        resultMap.put("hours", hours);
        resultMap.put("dataPoints", curve.size());
        resultMap.put("maxTemperature", Math.round(maxTemp * 10.0) / 10.0);
        resultMap.put("minTemperature", Math.round(minTemp * 10.0) / 10.0);
        resultMap.put("avgTemperature", Math.round(avgTemp * 10.0) / 10.0);
        resultMap.put("curve", curve);

        return resultMap;
    }

    /**
     * 获取车辆当前温度状态
     */
    public Map<String, Object> getCurrentStatus(String vehicleId) {
        Set<String> keys = redisTemplate.keys("coldchain:latest:" + vehicleId + ":*");
        if (keys == null || keys.isEmpty()) {
            return Map.of("vehicleId", vehicleId, "status", "NO_DATA");
        }

        List<Map<String, Object>> compartments = new ArrayList<>();
        boolean hasAlert = false;

        for (String key : keys) {
            Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
            String compartment = key.substring(key.lastIndexOf(":") + 1);
            double temp = Double.parseDouble(data.getOrDefault("temperature", "0").toString());
            String coldType = data.getOrDefault("coldType", "FROZEN").toString();

            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("compartment", compartment);
            comp.put("temperature", temp);
            comp.put("humidity", data.get("humidity"));
            comp.put("coldType", coldType);
            comp.put("lastUpdate", data.get("timestamp"));
            comp.put("inRange", isInRange(temp, coldType));

            if (!isInRange(temp, coldType)) hasAlert = true;
            compartments.add(comp);
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("vehicleId", vehicleId);
        status.put("compartments", compartments);
        status.put("overallStatus", hasAlert ? "ALERT" : "NORMAL");
        status.put("alertCount", getAlertCount(vehicleId));
        return status;
    }

    /**
     * 获取告警历史
     */
    public List<Map<String, Object>> getAlertHistory(String vehicleId, int limit) {
        List<Map<String, Object>> alerts = alertHistory.getOrDefault(vehicleId, new ArrayList<>());
        int start = Math.max(0, alerts.size() - limit);
        return alerts.subList(start, alerts.size());
    }

    // ========== 私有方法 ==========
    private boolean checkTemperatureAlert(String vehicleId, String compartment,
                                          double temperature, String coldType) {
        double minTemp, maxTemp;
        if ("FROZEN".equalsIgnoreCase(coldType)) {
            minTemp = DEFAULT_MIN_TEMP;
            maxTemp = DEFAULT_MAX_TEMP;
        } else {
            minTemp = REFRIGERATED_MIN;
            maxTemp = REFRIGERATED_MAX;
        }
        return temperature < minTemp || temperature > maxTemp;
    }

    private boolean isInRange(double temperature, String coldType) {
        if ("FROZEN".equalsIgnoreCase(coldType)) {
            return temperature >= DEFAULT_MIN_TEMP && temperature <= DEFAULT_MAX_TEMP;
        } else {
            return temperature >= REFRIGERATED_MIN && temperature <= REFRIGERATED_MAX;
        }
    }

    private String generateAlertMessage(String vehicleId, String compartment,
                                         double temperature, String coldType) {
        double minTemp, maxTemp;
        if ("FROZEN".equalsIgnoreCase(coldType)) {
            minTemp = DEFAULT_MIN_TEMP;
            maxTemp = DEFAULT_MAX_TEMP;
        } else {
            minTemp = REFRIGERATED_MIN;
            maxTemp = REFRIGERATED_MAX;
        }

        if (temperature > maxTemp) {
            return String.format("温度过高告警: 车辆%s货舱%s当前温度%.1f℃，超过上限%.1f℃",
                    vehicleId, compartment, temperature, maxTemp);
        } else {
            return String.format("温度过低告警: 车辆%s货舱%s当前温度%.1f℃，低于下限%.1f℃",
                    vehicleId, compartment, temperature, minTemp);
        }
    }

    private void recordAlert(String vehicleId, String compartment,
                              double temperature, String coldType, String message) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("vehicleId", vehicleId);
        alert.put("compartment", compartment);
        alert.put("temperature", temperature);
        alert.put("coldType", coldType);
        alert.put("message", message);
        alert.put("time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        alertHistory.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(alert);

        // Redis 记录告警次数
        String countKey = "coldchain:alert_count:" + vehicleId;
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, 24, TimeUnit.HOURS);
    }

    private int getAlertCount(String vehicleId) {
        String countKey = "coldchain:alert_count:" + vehicleId;
        String count = redisTemplate.opsForValue().get(countKey);
        return count != null ? Integer.parseInt(count) : 0;
    }
}
