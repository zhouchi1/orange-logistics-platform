package com.orange.logistics.im.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.im.handler.*;
import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.session.ChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息处理 Handler
 * 根据消息类型分发到不同 Handler 处理
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChannelManager channelManager;
    private final AuthHandler authHandler;
    private final ChatHandler chatHandler;
    private final GroupChatHandler groupChatHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final AckHandler ackHandler;
    private final NotifyHandler notifyHandler;

    public WebSocketHandler(ChannelManager channelManager,
                            AuthHandler authHandler,
                            ChatHandler chatHandler,
                            GroupChatHandler groupChatHandler,
                            HeartbeatHandler heartbeatHandler,
                            AckHandler ackHandler,
                            NotifyHandler notifyHandler) {
        this.channelManager = channelManager;
        this.authHandler = authHandler;
        this.chatHandler = chatHandler;
        this.groupChatHandler = groupChatHandler;
        this.heartbeatHandler = heartbeatHandler;
        this.ackHandler = ackHandler;
        this.notifyHandler = notifyHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        log.debug("[WS] 收到消息: channel={}, content={}", ctx.channel().id().asShortText(), text);

        try {
            ImMessage message = objectMapper.readValue(text, ImMessage.class);

            if (message.getType() == null) {
                sendError(ctx, "消息类型不能为空");
                return;
            }

            // 非认证消息需要检查是否已认证
            if (message.getType() != MessageType.AUTH && !channelManager.isAuthenticated(ctx.channel())) {
                sendError(ctx, "请先进行认证");
                return;
            }

            // 根据消息类型分发
            switch (message.getType()) {
                case AUTH -> authHandler.handle(ctx, message);
                case CHAT -> chatHandler.handle(ctx, message);
                case GROUP_CHAT -> groupChatHandler.handle(ctx, message);
                case ACK -> ackHandler.handle(ctx, message);
                case HEARTBEAT -> heartbeatHandler.handle(ctx, message);
                case NOTIFY -> notifyHandler.handle(ctx, message);
                case RECALL -> chatHandler.handleRecall(ctx, message);
                case TYPING -> chatHandler.handleTyping(ctx, message);
                default -> sendError(ctx, "不支持的消息类型: " + message.getType());
            }
        } catch (Exception e) {
            log.error("[WS] 消息处理异常: {}", e.getMessage(), e);
            sendError(ctx, "消息格式错误");
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.info("[WS] 新连接: channel={}, remote={}",
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.info("[WS] 连接断开: channel={}", ctx.channel().id().asShortText());
        channelManager.removeChannel(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[WS] 连接异常: channel={}, error={}",
                ctx.channel().id().asShortText(), cause.getMessage());
        channelManager.removeChannel(ctx.channel());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleEvent) {
            if (idleEvent.state() == IdleState.READER_IDLE) {
                log.warn("[WS] 读空闲超时，断开连接: channel={}", ctx.channel().id().asShortText());
                channelManager.removeChannel(ctx.channel());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            ImMessage errorMsg = ImMessage.builder()
                    .type(MessageType.NOTIFY)
                    .content(message)
                    .timestamp(System.currentTimeMillis())
                    .build();
            String json = objectMapper.writeValueAsString(errorMsg);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("[WS] 发送错误消息失败", e);
        }
    }
}
