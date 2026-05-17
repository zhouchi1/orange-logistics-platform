package com.orange.logistics.im.handler;

import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageCodec;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.OfflineMessageService;
import com.orange.logistics.im.service.SessionService;
import com.orange.logistics.im.session.ChannelManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 连接认证 Handler
 * 验证 JWT Token，建立用户会 */
@Slf4j
@Component
public class AuthHandler {

    @Value("${im.jwt.secret:orange-logistics-platform-secret-key-2024-must-be-256-bits}")
    private String jwtSecret;

    private final SessionService sessionService;
    private final OfflineMessageService offlineMessageService;
    private final MessageCodec messageCodec;
    private final ChannelManager channelManager;

    public AuthHandler(SessionService sessionService,
                       OfflineMessageService offlineMessageService,
                       MessageCodec messageCodec,
                       ChannelManager channelManager) {
        this.sessionService = sessionService;
        this.offlineMessageService = offlineMessageService;
        this.messageCodec = messageCodec;
        this.channelManager = channelManager;
    }

    public void handle(ChannelHandlerContext ctx, ImMessage message) {
        String token = message.getToken();
        if (token == null || token.isEmpty()) {
            sendAuthResult(ctx, false, "Token不能为空");
            return;
        }

        try {
            // 验证 JWT
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String roles = claims.get("roles", String.class);
            String deviceType = message.getExtra() != null
                    ? (String) message.getExtra().get("deviceType")
                    : "WEB";

            // 注册用户会话
            sessionService.userOnline(userId, username, roles, ctx.channel(), deviceType)
                    .subscribe(
                            v -> {
                                log.info("[Auth] 用户认证成功: userId={}, username={}", userId, username);
                                sendAuthResult(ctx, true, "认证成功");

                                // 推送离线消                                pushOfflineMessages(ctx, userId);
                            },
                            error -> {
                                log.error("[Auth] 会话注册失败: {}", error.getMessage());
                                sendAuthResult(ctx, false, "会话注册失败");
                            }
                    );

        } catch (Exception e) {
            log.warn("[Auth] Token验证失败: {}", e.getMessage());
            sendAuthResult(ctx, false, "Token无效或已过期");
            // 认证失败，关闭连            ctx.close();
        }
    }

    private void sendAuthResult(ChannelHandlerContext ctx, boolean success, String message) {
        ImMessage response = ImMessage.builder()
                .type(MessageType.AUTH)
                .content(message)
                .status(success ? "SUCCESS" : "FAILED")
                .timestamp(System.currentTimeMillis())
                .build();

        String json = messageCodec.encode(response);
        if (json != null) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    private void pushOfflineMessages(ChannelHandlerContext ctx, String userId) {
        offlineMessageService.pullOfflineMessages(userId)
                .subscribe(msg -> {
                    String json = messageCodec.encode(msg);
                    if (json != null && ctx.channel().isActive()) {
                        ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
                    }
                });
    }
}
