package com.orange.logistics.notification.service;

import com.orange.logistics.notification.enums.ChannelType;
import reactor.core.publisher.Mono;

/**
 * 邮件发送服务
 */
@org.springframework.stereotype.Service
public class EmailService {

    /**
     * 发送邮件
     *
     * @param email   收件人邮箱
     * @param title   邮件标题
     * @param content 邮件内容
     * @return 发送结果
     */
    public Mono<Boolean> send(String email, String title, String content) {
        // 模拟调用邮件服务（如 JavaMail / SendGrid）
        return Mono.fromCallable(() -> {
            if (email == null || !email.contains("@")) {
                throw new RuntimeException("无效邮箱地址: " + email);
            }
            // 模拟发送延迟
            Thread.sleep(100);
            return true;
        }).onErrorReturn(false);
    }

    public ChannelType getChannel() {
        return ChannelType.EMAIL;
    }
}
