package com.orange.logistics.im.handler;

import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageCodec;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.GroupService;
import com.orange.logistics.im.service.MessageService;
import com.orange.logistics.im.service.PushService;
import com.orange.logistics.im.session.ChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 群聊消息处理 Handler
 * 处理群聊消息的发送和广播
 */
@Slf4j
@Component
public class GroupChatHandler {

    private final MessageService messageService;
    private final GroupService groupService;
    private final PushService pushService;
    private final ChannelManager channelManager;
    private final MessageCodec messageCodec;

    public GroupChatHandler(MessageService messageService,
                            GroupService groupService,
                            PushService pushService,
                            ChannelManager channelManager,
                            MessageCodec messageCodec) {
        this.messageService = messageService;
        this.groupService = groupService;
        this.pushService = pushService;
        this.channelManager = channelManager;
        this.messageCodec = messageCodec;
    }

    /**
     * 处理群聊消息
     */
    public void handle(ChannelHandlerContext ctx, ImMessage message) {
        String fromUserId = channelManager.getUserId(ctx.channel());
        if (fromUserId == null) {
            sendError(ctx, "用户未认证");
            return;
        }

        String groupId = message.getGroupId();
        if (groupId == null || groupId.isEmpty()) {
            sendError(ctx, "群组ID不能为空");
            return;
        }

        // 验证是否是群成员
        groupService.isMember(groupId, fromUserId)
                .subscribe(isMember -> {
                    if (!isMember) {
                        sendError(ctx, "您不是该群成员");
                        return;
                    }

                    // 检查是否被禁言
                    groupService.isMuted(groupId, fromUserId)
                            .subscribe(isMuted -> {
                                if (isMuted) {
                                    sendError(ctx, "您已被禁言");
                                    return;
                                }

                                // 发送群消息
                                doSendGroupMessage(ctx, message, fromUserId, groupId);
                            });
                });
    }

    private void doSendGroupMessage(ChannelHandlerContext ctx, ImMessage message,
                                     String fromUserId, String groupId) {
        // 1. 生成消息ID
        String messageId = messageService.generateMessageId();
        message.setMessageId(messageId);
        message.setFromUserId(fromUserId);
        message.setFromUserName(channelManager.getSession(ctx.channel()).getUsername());
        message.setTimestamp(System.currentTimeMillis());
        message.setType(MessageType.GROUP_CHAT);
        message.setGroupId(groupId);
        message.setStatus("SENT");

        if (message.getContentType() == null) {
            message.setContentType("TEXT");
        }

        // 2. 异步存储消息
        messageService.saveMessage(message).subscribe();

        // 3. 获取群成员列表并推送
        groupService.getGroupMemberIds(groupId)
                .collectList()
                .subscribe(memberIds -> {
                    Set<String> memberSet = new HashSet<>(memberIds);
                    pushService.pushToGroup(memberSet, message, fromUserId).subscribe();
                    log.debug("[GroupChat] 群消息发送: from={}, groupId={}, messageId={}, members={}",
                            fromUserId, groupId, messageId, memberIds.size());
                });

        // 4. 返回发送确认
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
