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
 * 消息确认 Handler（已读回执）
 * 处理消息的送达确认和已读确认
 */
@Slf4j
@Component
public class AckHandler {

    private final MessageService messageService;
    private final PushService pushService;
    private final ChannelManager channelManager;
    private final MessageCodec messageCodec;

    public AckHandler(MessageService messageService,
                      PushService pushService,
                      ChannelManager channelManager,
                      MessageCodec messageCodec) {
        this.messageService = messageService;
        this.pushService = pushService;
        this.channelManager = channelManager;
        this.messageCodec = messageCodec;
    }

    public void handle(ChannelHandlerContext ctx, ImMessage message) {
        String userId = channelManager.getUserId(ctx.channel());
        if (userId == null) return;

        String messageId = message.getMessageId();
        String status = message.getStatus(); // DELIVERED or READ

        if (messageId == null || messageId.isEmpty()) {
            return;
        }

        if (status == null) {
            status = "READ";
        }

        // 更新消息状态
        messageService.updateMessageStatus(messageId, status).subscribe();

        // 如果是已读回执，通知发送者
        if ("READ".equals(status) && message.getFromUserId() != null) {
            ImMessage readReceipt = ImMessage.builder()
                    .type(MessageType.ACK)
                    .messageId(messageId)
                    .fromUserId(userId)
                    .status("READ")
                    .timestamp(System.currentTimeMillis())
                    .build();

            pushService.pushToUser(message.getFromUserId(), readReceipt).subscribe();
            log.debug("[ACK] 已读回执: messageId={}, reader={}", messageId, userId);
        }

        // 批量已读（标记与某用户的所有消息为已读）
        if (message.getToId() != null && "READ_ALL".equals(status)) {
            messageService.markAsRead(userId, message.getToId()).subscribe();
            log.debug("[ACK] 批量已读: userId={}, fromUserId={}", userId, message.getToId());
        }
    }
}
