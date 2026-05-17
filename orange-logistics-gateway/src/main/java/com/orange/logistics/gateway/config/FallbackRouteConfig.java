package com.orange.logistics.gateway.config;

import com.orange.logistics.gateway.handler.FallbackHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;

/**
 * 路由降级配置
 */
@Configuration
public class FallbackRouteConfig {

    @Bean
    public RouterFunction<ServerResponse> fallbackRoute(FallbackHandler fallbackHandler) {
        return RouterFunctions.route(path("/fallback/**"), fallbackHandler);
    }
}
