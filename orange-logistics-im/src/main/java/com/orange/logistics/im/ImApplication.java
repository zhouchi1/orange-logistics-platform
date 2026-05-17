package com.orange.logistics.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * IM 即时通讯服务启动 * 基于 Netty + WebSocket 的企业级即时通讯服务
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class ImApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImApplication.class, args);
    }
}
