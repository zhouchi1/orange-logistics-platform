package com.orange.logistics.im.handler;

import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageCodec;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.SessionService;
import com.orange.logistics.im.session.ChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 心跳检测 Handler
 * 客户端每 30s 发送心跳，服务端响应并更新活跃时间
 */
@Slf4j
@Component
public class HeartbeatHandler {

    private final ChannelManager channelManager;
    private final SessionService sessionService;
    private final MessageCodec messageCodec;

    public HeartbeatHandler(ChannelManager channelManager,
                            SessionService sessionService,
                            MessageCodec messageCodec) {
        this.channelManager = channelManager;
        this.sessionService = sessionService;
        this.messageCodec = messageCodec;
    }

    public void handle(ChannelHandlerContext ctx, ImMessage message) {
        String userId = channelManager.getUserId(ctx.channel());
        if (userId == null) {
            return;
        }

        // 更新活跃时间
        sessionService.updateActiveTime(userId).subscribe();

        // 响应心跳，携带服务器时间（用于客户端校时）
        ImMessage pong = ImMessage.builder()
                .type(MessageType.HEARTBEAT)
                .content("PONG")
                .timestamp(System.currentTimeMillis())
                .build();

        String json = messageCodec.encode(pong);
        if (json != null) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
        }

        log.trace("[Heartbeat] userId={}, channelId={}", userId, ctx.channel().id().asShortText());
    }
}
