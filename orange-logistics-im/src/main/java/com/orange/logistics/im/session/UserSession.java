package com.orange.logistics.im.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户会话信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户     */
    private String username;

    /**
     * 角色
     */
    private String roles;

    /**
     * Channel ID
     */
    private String channelId;

    /**
     * 设备类型：PC, MOBILE, WEB
     */
    private String deviceType;

    /**
     * 连接时间
     */
    private Long connectTime;

    /**
     * 最后活跃时     */
    private Long lastActiveTime;

    /**
     * 服务器节点标识（多实例部署时使用     */
    private String serverNode;
}
