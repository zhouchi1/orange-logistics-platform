package com.orange.logistics.im.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线状态响 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineStatusResponse {

    private String userId;
    private String username;
    private Boolean online;
    private Long lastActiveTime;
    private String deviceType;
}
