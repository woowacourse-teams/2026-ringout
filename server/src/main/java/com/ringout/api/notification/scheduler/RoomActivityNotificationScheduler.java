package com.ringout.api.notification.scheduler;

import com.ringout.api.notification.service.NotificationSendService;
import com.ringout.api.notification.service.RoomActivityNotificationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomActivityNotificationScheduler {

    private final RoomActivityNotificationService roomActivityNotificationService;
    private final NotificationSendService notificationSendService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *")
    public void notifyRoomActivity() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        log.debug("방 활동 알림 스케줄러 실행 - {}", now);
        try {
            List<Long> notificationIds = roomActivityNotificationService.create(now);
            notificationSendService.send(notificationIds);
        } catch (Exception e) {
            log.error("방 활동 알림 처리 실패 - {}", now, e);
        }
    }
}
