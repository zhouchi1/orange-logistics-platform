package com.orange.logistics.notification.service;

import com.orange.logistics.notification.enums.ChannelType;
import reactor.core.publisher.Mono;

/**
 * 微信消息推送服 */
@org.springframework.stereotype.Service
public class WechatService {

    /**
     * 发送微信模板消     *
     * @param openId  用户微信openId
     * @param content 消息内容（JSON格式模板数据     * @return 发送结算     */
    public Mono<Boolean> send(String openId, String content) {
        // 模拟调用微信模板消息API
        return Mono.fromCallable(() -> {
            if (openId == null || openId.isEmpty()) {
                throw new RuntimeException("微信openId为空");
            }
            // 模拟微信API调用延迟
            Thread.sleep(80);
            return true;
        }).onErrorReturn(false);
    }

    public ChannelType getChannel() {
        return ChannelType.WECHAT;
    }
}
