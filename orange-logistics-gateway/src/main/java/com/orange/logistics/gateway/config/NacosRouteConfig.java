package com.orange.logistics.gateway.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Nacos 动态路由配置
 * 监听 Nacos 配置变更，动态刷新路由规则（不重启）
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.cloud.nacos.config.enabled", havingValue = "true", matchIfMissing = true)
public class NacosRouteConfig implements RouteDefinitionRepository {

    private final NacosConfigManager nacosConfigManager;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gateway.dynamic-route.data-id:gateway-routes.json}")
    private String dataId;

    @Value("${gateway.dynamic-route.group:DEFAULT_GROUP}")
    private String group;

    private volatile List<RouteDefinition> routeDefinitions = new ArrayList<>();

    public NacosRouteConfig(NacosConfigManager nacosConfigManager,
                            ApplicationEventPublisher publisher) {
        this.nacosConfigManager = nacosConfigManager;
        this.publisher = publisher;
    }

    @PostConstruct
    public void init() {
        try {
            // 初始加载路由配置
            String configContent = nacosConfigManager.getConfigService()
                    .getConfig(dataId, group, 5000);
            if (configContent != null && !configContent.isEmpty()) {
                updateRoutes(configContent);
            }

            // 监听配置变更
            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("[NacosRoute] 收到路由配置变更通知");
                    updateRoutes(configInfo);
                    // 发布路由刷新事件
                    publisher.publishEvent(new RefreshRoutesEvent(this));
                }
            });

            log.info("[NacosRoute] Nacos 动态路由监听器初始化完成, dataId={}, group={}", dataId, group);
        } catch (Exception e) {
            log.error("[NacosRoute] 初始化动态路由失败: {}", e.getMessage(), e);
        }
    }

    private void updateRoutes(String configContent) {
        try {
            List<RouteDefinition> definitions = objectMapper.readValue(
                    configContent, new TypeReference<List<RouteDefinition>>() {});
            this.routeDefinitions = definitions;
            log.info("[NacosRoute] 路由配置更新成功，共 {} 条路由", definitions.size());
        } catch (Exception e) {
            log.error("[NacosRoute] 解析路由配置失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routeDefinitions);
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r -> {
            routeDefinitions.add(r);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            routeDefinitions.removeIf(rd -> rd.getId().equals(id));
            return Mono.empty();
        });
    }
}
