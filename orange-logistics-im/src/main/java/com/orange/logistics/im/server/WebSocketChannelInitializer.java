package com.orange.logistics.im.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket Channel 初始化器
 * 配置 HTTP 编解码、WebSocket 协议处理、空闲检测等
 */
@Component
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Value("${im.netty.max-frame-size:65536}")
    private int maxFrameSize;

    @Value("${im.heartbeat.timeout:60}")
    private int heartbeatTimeout;

    private final WebSocketHandler webSocketHandler;

    public WebSocketChannelInitializer(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // 空闲检测：读空闲超时断开连接
        pipeline.addLast("idleStateHandler",
                new IdleStateHandler(heartbeatTimeout, 0, 0, TimeUnit.SECONDS));

        // HTTP 编解码
        pipeline.addLast("httpServerCodec", new HttpServerCodec());

        // HTTP 消息聚合（处理大请求体）
        pipeline.addLast("httpObjectAggregator", new HttpObjectAggregator(maxFrameSize));

        // 大文件传输支持
        pipeline.addLast("chunkedWriteHandler", new ChunkedWriteHandler());

        // WebSocket 协议处理（握手、心跳帧、关闭帧）
        pipeline.addLast("webSocketServerProtocolHandler",
                new WebSocketServerProtocolHandler("/ws", null, true, maxFrameSize));

        // 业务消息处理
        pipeline.addLast("webSocketHandler", webSocketHandler);
    }
}
