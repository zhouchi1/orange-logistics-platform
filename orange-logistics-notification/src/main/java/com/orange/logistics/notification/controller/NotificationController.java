package com.orange.logistics.notification.controller;

import com.orange.logistics.notification.dto.NotificationResponse;
import com.orange.logistics.notification.dto.SendNotificationRequest;
import com.orange.logistics.notification.entity.NotificationRecord;
import com.orange.logistics.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 通知服务接口
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 发送通知
     */
    @PostMapping("/send")
    public Mono<NotificationResponse> send(@RequestBody SendNotificationRequest request) {
        return notificationService.sendNotification(request);
    }

    /**
     * 查询用户通知记录
     */
    @GetMapping("/records/{userId}")
    public Flux<NotificationRecord> getUserRecords(@PathVariable String userId) {
        return notificationService.getUserRecords(userId);
    }
}
