package com.orange.logistics.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统版本信息接口
 */
@RestController
public class VersionController {

    @Value("${spring.application.name:orange-logistics-gateway}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String version;

    @GetMapping("/version")
    public Mono<Map<String, Object>> version() {
        return Mono.just(Map.of(
                "app", appName,
                "version", version,
                "timestamp", LocalDateTime.now().toString(),
                "java", System.getProperty("java.version"),
                "status", "running"
        ));
    }
}
