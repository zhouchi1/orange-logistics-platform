package com.orange.logistics.im.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.im.entity.Message;
import com.orange.logistics.im.protocol.ImMessage;
import com.orange.logistics.im.protocol.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息服务
 * 负责消息的发送、存储、查 */
@Slf4j
@Service
public class MessageService {

    private final R2dbcEntityTemplate r2dbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 雪花算法生成消息ID
     */
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    public MessageService(R2dbcEntityTemplate r2dbcTemplate) {
        this.r2dbcTemplate = r2dbcTemplate;
    }

    /**
     * 生成消息ID
     */
    public String generateMessageId() {
        return String.valueOf(snowflake.nextId());
    }

    /**
     * 保存消息到数据库
     */
    public Mono<Message> saveMessage(ImMessage imMessage) {
        String extraJson = null;
        if (imMessage.getExtra() != null) {
            try {
                extraJson = objectMapper.writeValueAsString(imMessage.getExtra());
            } catch (JsonProcessingException e) {
                log.warn("[MessageService] 序列化extra失败: {}", e.getMessage());
            }
        }

        Message message = Message.builder()
                .messageId(imMessage.getMessageId())
                .messageType(imMessage.getType() != null ? imMessage.getType().name() : "CHAT")
                .fromUserId(imMessage.getFromUserId())
                .fromUserName(imMessage.getFromUserName())
                .toId(imMessage.getToId())
                .groupId(imMessage.getGroupId())
                .content(imMessage.getContent())
                .contentType(imMessage.getContentType() != null ? imMessage.getContentType() : "TEXT")
                .extra(extraJson)
                .status("SENT")
                .recalled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return r2dbcTemplate.insert(message)
                .doOnSuccess(m -> log.debug("[MessageService] 消息已存 messageId={}", m.getMessageId()))
                .onErrorResume(e -> {
                    log.error("[MessageService] 消息存储失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 查询单聊历史消息
     */
    public Flux<Message> getChatHistory(String userId1, String userId2, int page, int size) {
        return r2dbcTemplate.select(Message.class)
                .matching(Query.query(
                        Criteria.where("message_type").is("CHAT")
                                .and(Criteria.where("from_user_id").is(userId1).and("to_id").is(userId2)
                                        .or(Criteria.where("from_user_id").is(userId2).and("to_id").is(userId1)))
                                .and("recalled").is(false)
                ).offset((long) page * size).limit(size))
                .all();
    }

    /**
     * 查询群聊历史消息
     */
    public Flux<Message> getGroupChatHistory(String groupId, int page, int size) {
        return r2dbcTemplate.select(Message.class)
                .matching(Query.query(
                        Criteria.where("group_id").is(groupId)
                                .and("recalled").is(false)
                ).offset((long) page * size).limit(size))
                .all();
    }

    /**
     * 更新消息状     */
    public Mono<Void> updateMessageStatus(String messageId, String status) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("UPDATE im_message SET status = :status, updated_at = :now WHERE message_id = :messageId")
                .bind("status", status)
                .bind("now", LocalDateTime.now())
                .bind("messageId", messageId)
                .then()
                .doOnSuccess(v -> log.debug("[MessageService] 消息状态更 messageId={}, status={}", messageId, status));
    }

    /**
     * 撤回消息分钟内）
     */
    public Mono<Boolean> recallMessage(String messageId, String userId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("UPDATE im_message SET recalled = true, updated_at = :now " +
                        "WHERE message_id = :messageId AND from_user_id = :userId " +
                        "AND created_at > :timeLimit")
                .bind("now", LocalDateTime.now())
                .bind("messageId", messageId)
                .bind("userId", userId)
                .bind("timeLimit", LocalDateTime.now().minusMinutes(2))
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    /**
     * 获取未读消息     */
    public Mono<Long> getUnreadCount(String userId, String fromUserId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT COUNT(*) as cnt FROM im_message " +
                        "WHERE to_id = :userId AND from_user_id = :fromUserId " +
                        "AND status != 'READ' AND recalled = false")
                .bind("userId", userId)
                .bind("fromUserId", fromUserId)
                .map(row -> row.get("cnt", Long.class))
                .first()
                .defaultIfEmpty(0L);
    }

    /**
     * 标记消息为已     */
    public Mono<Void> markAsRead(String userId, String fromUserId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("UPDATE im_message SET status = 'READ', updated_at = :now " +
                        "WHERE to_id = :userId AND from_user_id = :fromUserId AND status != 'READ'")
                .bind("now", LocalDateTime.now())
                .bind("userId", userId)
                .bind("fromUserId", fromUserId)
                .then();
    }
}
