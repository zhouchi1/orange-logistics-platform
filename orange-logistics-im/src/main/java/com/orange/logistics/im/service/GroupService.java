package com.orange.logistics.im.service;

import com.orange.logistics.im.entity.GroupInfo;
import com.orange.logistics.im.entity.GroupMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 群组服务
 * 创建群、加入、退出、群成员管理
 */
@Slf4j
@Service
public class GroupService {

    private final R2dbcEntityTemplate r2dbcTemplate;

    public GroupService(R2dbcEntityTemplate r2dbcTemplate) {
        this.r2dbcTemplate = r2dbcTemplate;
    }

    /**
     * 创建群组
     */
    public Mono<GroupInfo> createGroup(String groupName, String ownerId, String groupType) {
        String groupId = "G_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        GroupInfo group = GroupInfo.builder()
                .groupId(groupId)
                .groupName(groupName)
                .ownerId(ownerId)
                .groupType(groupType != null ? groupType : "TEAM")
                .memberCount(1)
                .maxMembers(500)
                .muteAll(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 创建群组并添加群主为成员
        return r2dbcTemplate.insert(group)
                .flatMap(savedGroup -> {
                    GroupMember ownerMember = GroupMember.builder()
                            .groupId(groupId)
                            .userId(ownerId)
                            .role("OWNER")
                            .muted(false)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    return r2dbcTemplate.insert(ownerMember).thenReturn(savedGroup);
                })
                .doOnSuccess(g -> log.info("[GroupService] 群组创建成功: groupId={}, name={}", g.getGroupId(), g.getGroupName()));
    }

    /**
     * 加入群组
     */
    public Mono<Boolean> joinGroup(String groupId, String userId, String username) {
        // 检查是否已是成员
        return isMember(groupId, userId)
                .flatMap(isMember -> {
                    if (isMember) {
                        return Mono.just(false);
                    }

                    GroupMember member = GroupMember.builder()
                            .groupId(groupId)
                            .userId(userId)
                            .username(username)
                            .role("MEMBER")
                            .muted(false)
                            .joinedAt(LocalDateTime.now())
                            .build();

                    return r2dbcTemplate.insert(member)
                            .then(updateMemberCount(groupId, 1))
                            .thenReturn(true);
                })
                .doOnSuccess(result -> {
                    if (result) {
                        log.info("[GroupService] 用户加入群组: groupId={}, userId={}", groupId, userId);
                    }
                });
    }

    /**
     * 退出群组
     */
    public Mono<Boolean> leaveGroup(String groupId, String userId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("DELETE FROM im_group_member WHERE group_id = :groupId AND user_id = :userId AND role != 'OWNER'")
                .bind("groupId", groupId)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows > 0) {
                        return updateMemberCount(groupId, -1).thenReturn(true);
                    }
                    return Mono.just(false);
                })
                .doOnSuccess(result -> {
                    if (result) {
                        log.info("[GroupService] 用户退出群组: groupId={}, userId={}", groupId, userId);
                    }
                });
    }

    /**
     * 踢出群成员
     */
    public Mono<Boolean> kickMember(String groupId, String operatorId, String targetUserId) {
        // 验证操作者权限（群主或管理员）
        return getMemberRole(groupId, operatorId)
                .flatMap(role -> {
                    if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
                        return Mono.just(false);
                    }
                    return leaveGroup(groupId, targetUserId);
                });
    }

    /**
     * 设置群成员禁言
     */
    public Mono<Void> muteMember(String groupId, String userId, boolean muted) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("UPDATE im_group_member SET muted = :muted WHERE group_id = :groupId AND user_id = :userId")
                .bind("muted", muted)
                .bind("groupId", groupId)
                .bind("userId", userId)
                .then();
    }

    /**
     * 更新群公告
     */
    public Mono<Void> updateAnnouncement(String groupId, String announcement) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("UPDATE im_group SET announcement = :announcement, updated_at = :now WHERE group_id = :groupId")
                .bind("announcement", announcement)
                .bind("now", LocalDateTime.now())
                .bind("groupId", groupId)
                .then();
    }

    /**
     * 获取群组信息
     */
    public Mono<GroupInfo> getGroupInfo(String groupId) {
        return r2dbcTemplate.select(GroupInfo.class)
                .matching(Query.query(Criteria.where("group_id").is(groupId)))
                .first();
    }

    /**
     * 获取群成员列表
     */
    public Flux<GroupMember> getGroupMembers(String groupId) {
        return r2dbcTemplate.select(GroupMember.class)
                .matching(Query.query(Criteria.where("group_id").is(groupId)))
                .all();
    }

    /**
     * 获取群成员ID列表
     */
    public Flux<String> getGroupMemberIds(String groupId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT user_id FROM im_group_member WHERE group_id = :groupId")
                .bind("groupId", groupId)
                .map(row -> row.get("user_id", String.class))
                .all();
    }

    /**
     * 判断是否是群成员
     */
    public Mono<Boolean> isMember(String groupId, String userId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT COUNT(*) as cnt FROM im_group_member WHERE group_id = :groupId AND user_id = :userId")
                .bind("groupId", groupId)
                .bind("userId", userId)
                .map(row -> row.get("cnt", Long.class))
                .first()
                .map(count -> count > 0)
                .defaultIfEmpty(false);
    }

    /**
     * 获取成员角色
     */
    public Mono<String> getMemberRole(String groupId, String userId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT role FROM im_group_member WHERE group_id = :groupId AND user_id = :userId")
                .bind("groupId", groupId)
                .bind("userId", userId)
                .map(row -> row.get("role", String.class))
                .first()
                .defaultIfEmpty("NONE");
    }

    /**
     * 获取用户加入的所有群组
     */
    public Flux<GroupInfo> getUserGroups(String userId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT g.* FROM im_group g INNER JOIN im_group_member m ON g.group_id = m.group_id WHERE m.user_id = :userId")
                .bind("userId", userId)
                .map((row, metadata) -> GroupInfo.builder()
                        .groupId(row.get("group_id", String.class))
                        .groupName(row.get("group_name", String.class))
                        .ownerId(row.get("owner_id", String.class))
                        .groupType(row.get("group_type", String.class))
                        .memberCount(row.get("member_count", Integer.class))
                        .announcement(row.get("announcement", String.class))
                        .build())
                .all();
    }

    /**
     * 判断成员是否被禁言
     */
    public Mono<Boolean> isMuted(String groupId, String userId) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("SELECT muted FROM im_group_member WHERE group_id = :groupId AND user_id = :userId")
                .bind("groupId", groupId)
                .bind("userId", userId)
                .map(row -> row.get("muted", Boolean.class))
                .first()
                .defaultIfEmpty(false);
    }

    private Mono<Void> updateMemberCount(String groupId, int delta) {
        return r2dbcTemplate.getDatabaseClient()
                .sql("UPDATE im_group SET member_count = member_count + :delta, updated_at = :now WHERE group_id = :groupId")
                .bind("delta", delta)
                .bind("now", LocalDateTime.now())
                .bind("groupId", groupId)
                .then();
    }
}
