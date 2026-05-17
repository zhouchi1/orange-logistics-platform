package com.orange.logistics.notification.service;

import com.orange.logistics.notification.enums.ChannelType;
import reactor.core.publisher.Mono;

/**
 * APP推送服务
 */
@org.springframework.stereotype.Service
public class PushService {

    /**
     * 发送APP推送
     *
     * @param userId  用户ID
     * @param title   推送标题
     * @param content 推送内容
     * @return 发送结果
     */
    public Mono<Boolean> send(String userId, String title, String content) {
        // 模拟调用推送服务（如极光推送、个推）
        return Mono.fromCallable(() -> {
            if (userId == null || userId.isEmpty()) {
                throw new RuntimeException("用户ID为空，无法推送");
            }
            // 模拟推送延迟
            Thread.sleep(30);
            return true;
        }).onErrorReturn(false);
    }

    public ChannelType getChannel() {
        return ChannelType.PUSH;
    }
}
