package com.orange.logistics.gateway.config;

import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.Set;

/**
 * 聚合各微服务 Swagger 文档配置
 * 通过 Gateway 路由自动发现各服务的 API 文档
 */
@Configuration
@EnableScheduling
public class SwaggerResourceConfig {

    private final RouteLocator routeLocator;
    private final SwaggerUiConfigProperties swaggerUiConfigProperties;

    /**
     * 不需要聚合文档的路由
     */
    private static final Set<String> EXCLUDED_ROUTES = Set.of(
            "websocket-service",
            "openapi"
    );

    public SwaggerResourceConfig(RouteLocator routeLocator,
                                  SwaggerUiConfigProperties swaggerUiConfigProperties) {
        this.routeLocator = routeLocator;
        this.swaggerUiConfigProperties = swaggerUiConfigProperties;
    }

    /**
     * 定时刷新 Swagger 资源列表
     */
    @Scheduled(fixedDelay = 30000)
    public void refreshSwaggerResources() {
        Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> urls = new HashSet<>();

        routeLocator.getRoutes()
                .filter(route -> !EXCLUDED_ROUTES.contains(route.getId()))
                .subscribe(route -> {
                    String routeId = route.getId();
                    // 构建各服务的 API 文档 URL
                    AbstractSwaggerUiConfigProperties.SwaggerUrl swaggerUrl =
                            new AbstractSwaggerUiConfigProperties.SwaggerUrl();
                    swaggerUrl.setName(routeId);
                    swaggerUrl.setUrl("/api/" + routeId + "/v3/api-docs");
                    urls.add(swaggerUrl);
                });

        swaggerUiConfigProperties.setUrls(urls);
    }
}
