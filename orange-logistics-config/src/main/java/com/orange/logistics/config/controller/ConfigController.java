package com.orange.logistics.config.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置中心接口
 * - 查看当前配置
 * - 配置刷新端点
 * - 配置健康检查
 */
@RefreshScope
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final Environment environment;

    @Value("${spring.application.name:orange-logistics-config}")
    private String applicationName;

    @Value("${spring.cloud.nacos.config.server-addr:localhost:8848}")
    private String nacosServerAddr;

    /**
     * 获取当前服务配置信息
     */
    @GetMapping("/info")
    public Mono<Map<String, Object>> getConfigInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("applicationName", applicationName);
        info.put("nacosServerAddr", nacosServerAddr);
        info.put("activeProfiles", environment.getActiveProfiles());
        info.put("timestamp", LocalDateTime.now().toString());
        return Mono.just(info);
    }

    /**
     * 获取指定配置项的值
     */
    @GetMapping("/property/{key}")
    public Mono<Map<String, String>> getProperty(@PathVariable String key) {
        String value = environment.getProperty(key);
        Map<String, String> result = new HashMap<>();
        result.put("key", key);
        result.put("value", value != null ? value : "NOT_FOUND");
        return Mono.just(result);
    }

    /**
     * 批量获取配置项
     */
    @PostMapping("/properties")
    public Mono<Map<String, String>> getProperties(@RequestBody java.util.List<String> keys) {
        Map<String, String> result = new HashMap<>();
        for (String key : keys) {
            String value = environment.getProperty(key);
            result.put(key, value != null ? value : "NOT_FOUND");
        }
        return Mono.just(result);
    }

    /**
     * 配置中心健康检查
     */
    @GetMapping("/health")
    public Mono<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("nacosAddr", nacosServerAddr);
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("configLoaded", true);
        return Mono.just(health);
    }

    /**
     * 获取共享配置列表
     */
    @GetMapping("/shared-configs")
    public Mono<Map<String, Object>> getSharedConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("sharedConfigs", java.util.List.of(
                Map.of("dataId", "common.yml", "group", "DEFAULT_GROUP", "description", "通用配置"),
                Map.of("dataId", "datasource.yml", "group", "DEFAULT_GROUP", "description", "数据源配置"),
                Map.of("dataId", "redis.yml", "group", "DEFAULT_GROUP", "description", "Redis配置")
        ));
        configs.put("nacosAddr", nacosServerAddr);
        return Mono.just(configs);
    }
}
