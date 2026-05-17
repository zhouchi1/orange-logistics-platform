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
 * 群成本 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("im_group_member")
public class GroupMember {

    @Id
    private Long id;

    /**
     * 群组ID
     */
    @Column("group_id")
    private String groupId;

    /**
     * 用户ID
     */
    @Column("user_id")
    private String userId;

    /**
     * 用户     */
    @Column("username")
    private String username;

    /**
     * 群内昵称
     */
    @Column("nickname")
    private String nickname;

    /**
     * 角色：OWNER, ADMIN, MEMBER
     */
    @Column("role")
    private String role;

    /**
     * 是否被禁言
     */
    @Column("muted")
    private Boolean muted;

    /**
     * 加入时间
     */
    @Column("joined_at")
    private LocalDateTime joinedAt;
}
