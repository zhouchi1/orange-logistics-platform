package com.orange.logistics.im.handler;

import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageCodec;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.MessageService;
import com.orange.logistics.im.service.PushService;
import com.orange.logistics.im.session.ChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 单聊消息处理 Handler
 * 处理单聊消息的发送、撤回、正在输入等
 */
@Slf4j
@Component
public class ChatHandler {

    private final MessageService messageService;
    private final PushService pushService;
    private final ChannelManager channelManager;
    private final MessageCodec messageCodec;

    public ChatHandler(MessageService messageService,
                       PushService pushService,
                       ChannelManager channelManager,
                       MessageCodec messageCodec) {
        this.messageService = messageService;
        this.pushService = pushService;
        this.channelManager = channelManager;
        this.messageCodec = messageCodec;
    }

    /**
     * 处理单聊消息
     * 流程：验证 -> 生成ID -> 存储 -> 推送 -> 确认
     */
    public void handle(ChannelHandlerContext ctx, ImMessage message) {
        String fromUserId = channelManager.getUserId(ctx.channel());
        if (fromUserId == null) {
            sendError(ctx, "用户未认证");
            return;
        }

        String toUserId = message.getToId();
        if (toUserId == null || toUserId.isEmpty()) {
            sendError(ctx, "接收者ID不能为空");
            return;
        }

        // 1. 生成消息ID
        String messageId = messageService.generateMessageId();
        message.setMessageId(messageId);
        message.setFromUserId(fromUserId);
        message.setFromUserName(channelManager.getSession(ctx.channel()).getUsername());
        message.setTimestamp(System.currentTimeMillis());
        message.setType(MessageType.CHAT);
        message.setStatus("SENT");

        if (message.getContentType() == null) {
            message.setContentType("TEXT");
        }

        // 2. 异步存储消息到数据库
        messageService.saveMessage(message).subscribe();

        // 3. 推送给接收者
        pushService.pushToUser(toUserId, message).subscribe();

        // 4. 返回发送成功确认给发送者
        ImMessage ack = ImMessage.builder()
                .type(MessageType.ACK)
                .messageId(messageId)
                .status("SENT")
                .timestamp(System.currentTimeMillis())
                .build();

        String ackJson = messageCodec.encode(ack);
        if (ackJson != null) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(ackJson));
        }

        log.debug("[Chat] 单聊消息发送: from={}, to={}, messageId={}", fromUserId, toUserId, messageId);
    }

    /**
     * 处理消息撤回
     */
    public void handleRecall(ChannelHandlerContext ctx, ImMessage message) {
        String userId = channelManager.getUserId(ctx.channel());
        String messageId = message.getMessageId();

        if (messageId == null || messageId.isEmpty()) {
            sendError(ctx, "消息ID不能为空");
            return;
        }

        messageService.recallMessage(messageId, userId)
                .subscribe(success -> {
                    if (success) {
                        // 通知对方消息已撤回
                        ImMessage recallNotify = ImMessage.builder()
                                .type(MessageType.RECALL)
                                .messageId(messageId)
                                .fromUserId(userId)
                                .toId(message.getToId())
                                .content("对方撤回了一条消息")
                                .timestamp(System.currentTimeMillis())
                                .build();

                        pushService.pushToUser(message.getToId(), recallNotify).subscribe();

                        // 确认撤回成功
                        ImMessage ack = ImMessage.builder()
                                .type(MessageType.ACK)
                                .messageId(messageId)
                                .status("RECALLED")
                                .timestamp(System.currentTimeMillis())
                                .build();
                        String json = messageCodec.encode(ack);
                        if (json != null) {
                            ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
                        }

                        log.info("[Chat] 消息撤回成功: userId={}, messageId={}", userId, messageId);
                    } else {
                        sendError(ctx, "撤回失败，消息已超过2分钟或非本人消息");
                    }
                });
    }

    /**
     * 处理正在输入状态
     */
    public void handleTyping(ChannelHandlerContext ctx, ImMessage message) {
        String fromUserId = channelManager.getUserId(ctx.channel());
        String toUserId = message.getToId();

        if (toUserId == null) return;

        ImMessage typingMsg = ImMessage.builder()
                .type(MessageType.TYPING)
                .fromUserId(fromUserId)
                .toId(toUserId)
                .timestamp(System.currentTimeMillis())
                .build();

        pushService.pushToUser(toUserId, typingMsg).subscribe();
    }

    private void sendError(ChannelHandlerContext ctx, String errorMsg) {
        ImMessage error = ImMessage.builder()
                .type(MessageType.NOTIFY)
                .content(errorMsg)
                .timestamp(System.currentTimeMillis())
                .build();
        String json = messageCodec.encode(error);
        if (json != null) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(json));
        }
    }
}
