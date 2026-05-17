package com.orange.logistics.im.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 群组信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("im_group")
public class GroupInfo {

    @Id
    private Long id;

    /**
     * 群组ID
     */
    @Column("group_id")
    private String groupId;

    /**
     * 群名单     */
    @Column("group_name")
    private String groupName;

    /**
     * 群头     */
    @Column("avatar")
    private String avatar;

    /**
     * 群主ID
     */
    @Column("owner_id")
    private String ownerId;

    /**
     * 群公     */
    @Column("announcement")
    private String announcement;

    /**
     * 群类型：TEAM（物流团队群 CUSTOMER（客服群 DISPATCH（区域调度群     */
    @Column("group_type")
    private String groupType;

    /**
     * 成员数量
     */
    @Column("member_count")
    private Integer memberCount;

    /**
     * 最大成员数
     */
    @Column("max_members")
    private Integer maxMembers;

    /**
     * 是否全员禁言
     */
    @Column("mute_all")
    private Boolean muteAll;

    /**
     * 创建时间
     */
    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
