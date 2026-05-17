package com.orange.logistics.im.controller;

import com.orange.logistics.im.dto.MessageResponse;
import com.orange.logistics.im.dto.SendMessageRequest;
import com.orange.logistics.im.dto.OnlineStatusResponse;
import com.orange.logistics.im.entity.GroupInfo;
import com.orange.logistics.im.entity.Message;
import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageType;
import com.orange.logistics.im.service.MessageService;
import com.orange.logistics.im.service.SessionService;
import com.orange.logistics.im.service.GroupService;
import com.orange.logistics.im.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * IM 消息 REST API
 * 提供历史消息查询、会话管理、在线状态等 HTTP 接口
 * （WebSocket 负责实时推送，REST 负责查询和管理）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/im")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final SessionService sessionService;
    private final GroupService groupService;
    private final PushService pushService;

    /**
     * 发送消息（HTTP 方式，适用于服务端调用）
     */
    @PostMapping("/messages/send")
    public Mono<Map<String, Object>> sendMessage(@RequestBody SendMessageRequest request) {
        String messageId = messageService.generateMessageId();

        ImMessage imMessage = ImMessage.builder()
                .messageId(messageId)
                .type(request.getGroupId() != null ? MessageType.GROUP_CHAT : MessageType.CHAT)
                .toId(request.getToId())
                .groupId(request.getGroupId())
                .content(request.getContent())
                .contentType(request.getContentType() != null ? request.getContentType() : "TEXT")
                .timestamp(System.currentTimeMillis())
                .build();

        return messageService.saveMessage(imMessage)
                .then(Mono.fromCallable(() -> {
                    // 推送给接收者
                    if (request.getToId() != null) {
                        pushService.pushToUser(request.getToId(), imMessage).subscribe();
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("messageId", messageId);
                    result.put("status", "SENT");
                    result.put("timestamp", System.currentTimeMillis());
                    return result;
                }));
    }

    /**
     * 查询单聊历史消息（分页）
     */
    @GetMapping("/messages/history")
    public Mono<Map<String, Object>> getHistory(
            @RequestParam String userId,
            @RequestParam String peerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return messageService.getChatHistory(userId, peerId, page, size)
                .map(this::toMessageResponse)
                .collectList()
                .map(messages -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("userId", userId);
                    result.put("peerId", peerId);
                    result.put("page", page);
                    result.put("size", size);
                    result.put("messages", messages);
                    return result;
                });
    }

    /**
     * 查询群聊历史消息
     */
    @GetMapping("/messages/group/{groupId}/history")
    public Mono<Map<String, Object>> getGroupHistory(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return messageService.getGroupChatHistory(groupId, page, size)
                .map(this::toMessageResponse)
                .collectList()
                .map(messages -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("groupId", groupId);
                    result.put("page", page);
                    result.put("size", size);
                    result.put("messages", messages);
                    return result;
                });
    }

    /**
     * 标记消息已读
     */
    @PostMapping("/messages/read")
    public Mono<Map<String, Object>> markAsRead(
            @RequestParam String userId,
            @RequestParam String peerId) {

        return messageService.markAsRead(userId, peerId)
                .thenReturn(Map.<String, Object>of(
                        "userId", userId,
                        "peerId", peerId,
                        "status", "READ"
                ));
    }

    /**
     * 撤回消息
     */
    @PostMapping("/messages/{messageId}/recall")
    public Mono<Map<String, Object>> recallMessage(
            @PathVariable String messageId,
            @RequestParam String userId) {

        return messageService.recallMessage(messageId, userId)
                .map(success -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("messageId", messageId);
                    result.put("recalled", success);
                    result.put("reason", success ? "撤回成功" : "超过2分钟无法撤回");
                    return result;
                });
    }

    /**
     * 获取未读消息数
     */
    @GetMapping("/messages/unread/count")
    public Mono<Map<String, Object>> getUnreadCount(
            @RequestParam String userId,
            @RequestParam String fromUserId) {

        return messageService.getUnreadCount(userId, fromUserId)
                .map(count -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("userId", userId);
                    result.put("fromUserId", fromUserId);
                    result.put("unreadCount", count);
                    return result;
                });
    }

    // ========== 在线状态 ==========

    /**
     * 查询用户在线状态
     */
    @GetMapping("/status/online")
    public Mono<OnlineStatusResponse> getOnlineStatus(@RequestParam String userId) {
        return sessionService.isUserOnline(userId)
                .map(online -> OnlineStatusResponse.builder()
                        .userId(userId)
                        .online(online)
                        .build());
    }

    /**
     * 批量查询在线状态
     */
    @PostMapping("/status/online/batch")
    public Flux<OnlineStatusResponse> batchOnlineStatus(@RequestBody List<String> userIds) {
        return Flux.fromIterable(userIds)
                .flatMap(userId -> sessionService.isUserOnline(userId)
                        .map(online -> OnlineStatusResponse.builder()
                                .userId(userId)
                                .online(online)
                                .build()));
    }

    /**
     * 获取在线用户数
     */
    @GetMapping("/status/online/count")
    public Mono<Map<String, Object>> getOnlineCount() {
        return sessionService.getOnlineCount()
                .map(count -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("onlineCount", count);
                    result.put("timestamp", System.currentTimeMillis());
                    return result;
                });
    }

    // ========== 群组管理 ==========

    /**
     * 创建群组
     */
    @PostMapping("/groups")
    public Mono<Map<String, Object>> createGroup(
            @RequestParam String name,
            @RequestParam String ownerId,
            @RequestParam(defaultValue = "TEAM") String groupType) {

        return groupService.createGroup(name, ownerId, groupType)
                .map(group -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("groupId", group.getGroupId());
                    result.put("name", group.getGroupName());
                    result.put("ownerId", group.getOwnerId());
                    result.put("memberCount", group.getMemberCount());
                    return result;
                });
    }

    /**
     * 获取群组信息
     */
    @GetMapping("/groups/{groupId}")
    public Mono<GroupInfo> getGroupInfo(@PathVariable String groupId) {
        return groupService.getGroupInfo(groupId);
    }

    /**
     * 加入群组
     */
    @PostMapping("/groups/{groupId}/join")
    public Mono<Map<String, Object>> joinGroup(
            @PathVariable String groupId,
            @RequestParam String userId,
            @RequestParam(required = false) String username) {

        return groupService.joinGroup(groupId, userId, username)
                .map(success -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("groupId", groupId);
                    result.put("userId", userId);
                    result.put("status", success ? "JOINED" : "ALREADY_MEMBER");
                    return result;
                });
    }

    /**
     * 退出群组
     */
    @PostMapping("/groups/{groupId}/leave")
    public Mono<Map<String, Object>> leaveGroup(
            @PathVariable String groupId,
            @RequestParam String userId) {

        return groupService.leaveGroup(groupId, userId)
                .map(success -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("groupId", groupId);
                    result.put("userId", userId);
                    result.put("status", success ? "LEFT" : "FAILED");
                    return result;
                });
    }

    /**
     * 获取用户的群组列表
     */
    @GetMapping("/groups/user/{userId}")
    public Flux<GroupInfo> getUserGroups(@PathVariable String userId) {
        return groupService.getUserGroups(userId);
    }

    // ========== 辅助方法 ==========

    private MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .fromUserId(message.getFromUserId())
                .fromUserName(message.getFromUserName())
                .toId(message.getToId())
                .groupId(message.getGroupId())
                .content(message.getContent())
                .contentType(message.getContentType())
                .status(message.getStatus())
                .recalled(message.getRecalled())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
