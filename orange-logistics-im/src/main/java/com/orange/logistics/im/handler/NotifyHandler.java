package com.orange.logistics.im.handler;

import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageCodec;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.PushService;
import com.orange.logistics.im.session.ChannelManager;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统通知 Handler
 * 处理物流状态变更推送等系统通知
 */
@Slf4j
@Component
public class NotifyHandler {

    private final PushService pushService;
    private final ChannelManager channelManager;
    private final MessageCodec messageCodec;

    public NotifyHandler(PushService pushService,
                         ChannelManager channelManager,
                         MessageCodec messageCodec) {
        this.pushService = pushService;
        this.channelManager = channelManager;
        this.messageCodec = messageCodec;
    }

    public void handle(ChannelHandlerContext ctx, ImMessage message) {
        // 系统通知一般由服务端主动推送，客户端发送的通知消息做转发处理
        String userId = channelManager.getUserId(ctx.channel());
        log.debug("[Notify] 收到通知消息: userId={}, content={}", userId, message.getContent());
    }

    /**
     * 推送物流状态变更通知
     */
    public void pushLogisticsNotification(String userId, String waybillNo, String status,
                                           String location, String description) {
        ImMessage notification = ImMessage.builder()
                .type(MessageType.NOTIFY)
                .content(description)
                .contentType("LOGISTICS_CARD")
                .timestamp(System.currentTimeMillis())
                .extra(Map.of(
                        "waybillNo", waybillNo,
                        "status", status,
                        "location", location != null ? location : "",
                        "notifyType", "LOGISTICS_STATUS_CHANGE"
                ))
                .build();

        pushService.pushToUser(userId, notification).subscribe();
        log.info("[Notify] 物流通知推送: userId={}, waybillNo={}, status={}", userId, waybillNo, status);
    }

    /**
     * 推送系统广播通知
     */
    public void pushSystemBroadcast(String title, String content) {
        ImMessage broadcast = ImMessage.builder()
                .type(MessageType.NOTIFY)
                .content(content)
                .contentType("SYSTEM")
                .timestamp(System.currentTimeMillis())
                .extra(Map.of(
                        "title", title,
                        "notifyType", "SYSTEM_BROADCAST"
                ))
                .build();

        pushService.broadcast(broadcast);
        log.info("[Notify] 系统广播: title={}", title);
    }
}
