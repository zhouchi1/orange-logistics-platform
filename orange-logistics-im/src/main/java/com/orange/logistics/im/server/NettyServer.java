package com.orange.logistics.im.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 服务器
 * 启动 WebSocket 服务，支持高并发连接
 */
@Slf4j
@Component
public class NettyServer {

    @Value("${im.netty.port:9001}")
    private int port;

    @Value("${im.netty.boss-threads:1}")
    private int bossThreads;

    @Value("${im.netty.worker-threads:8}")
    private int workerThreads;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final WebSocketChannelInitializer channelInitializer;

    public NettyServer(WebSocketChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
    }

    @PostConstruct
    public void start() {
        new Thread(this::doStart, "netty-server-starter").start();
    }

    private void doStart() {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    // TCP 连接队列大小
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    // 开启 TCP 心跳
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    // 禁用 Nagle 算法，减少延迟
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    // 发送缓冲区
                    .childOption(ChannelOption.SO_SNDBUF, 65536)
                    // 接收缓冲区
                    .childOption(ChannelOption.SO_RCVBUF, 65536);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("========================================");
            log.info("  Netty WebSocket Server started");
            log.info("  Port: {}", port);
            log.info("  Boss threads: {}", bossThreads);
            log.info("  Worker threads: {}", workerThreads);
            log.info("  WebSocket path: /ws");
            log.info("========================================");

            // 等待服务端口关闭
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[NettyServer] 服务器启动被中断", e);
        } catch (Exception e) {
            log.error("[NettyServer] 服务器启动失败", e);
        } finally {
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[NettyServer] 正在关闭 Netty 服务器...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("[NettyServer] Netty 服务器已关闭");
    }
}
