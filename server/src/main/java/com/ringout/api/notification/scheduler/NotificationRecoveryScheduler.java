package com.ringout.api.notification.scheduler;

import com.ringout.api.notification.service.NotificationSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRecoveryScheduler {

    private final NotificationSendService notificationSendService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recover() {
        try {
            notificationSendService.recover();
        } catch (Exception e) {
            log.error("미발송 알림 복구 실패", e);
        }
    }
}
