package com.orange.logistics.notification.service;

import com.orange.logistics.notification.enums.ChannelType;
import reactor.core.publisher.Mono;

/**
 * 短信发送服务
 */
@org.springframework.stereotype.Service
public class SmsService {

    /**
     * 发送短信
     *
     * @param phone   手机号
     * @param content 短信内容
     * @return 发送结果
     */
    public Mono<Boolean> send(String phone, String content) {
        // 模拟调用短信网关（如阿里云SMS、腾讯云SMS）
        return Mono.fromCallable(() -> {
            // 实际生产中对接短信服务商API
            if (phone == null || phone.length() < 11) {
                throw new RuntimeException("无效手机号: " + phone);
            }
            // 模拟网络延迟
            Thread.sleep(50);
            return true;
        }).onErrorReturn(false);
    }

    public ChannelType getChannel() {
        return ChannelType.SMS;
    }
}
