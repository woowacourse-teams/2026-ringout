package com.ringout.api.notification.scheduler;

import static com.ringout.api.config.NotificationAsyncConfig.NOTIFICATION_SEND_EXECUTOR;

import com.ringout.api.notification.service.NotificationSendService;
import com.ringout.api.notification.service.RoomActivityNotificationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RoomActivityNotificationScheduler {

    private final RoomActivityNotificationService roomActivityNotificationService;
    private final NotificationSendService notificationSendService;
    private final Clock clock;
    private final Executor notificationSendExecutor;

    public RoomActivityNotificationScheduler(RoomActivityNotificationService roomActivityNotificationService,
        NotificationSendService notificationSendService, Clock clock,
        @Qualifier(NOTIFICATION_SEND_EXECUTOR) Executor notificationSendExecutor) {
        this.roomActivityNotificationService = roomActivityNotificationService;
        this.notificationSendService = notificationSendService;
        this.clock = clock;
        this.notificationSendExecutor = notificationSendExecutor;
    }

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "roomActivityNotification", lockAtLeastFor = "30s", lockAtMostFor = "50s")
    public void notifyRoomActivity() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        log.debug("방 활동 알림 스케줄러 실행 - {}", now);
        try {
            List<Long> notificationIds = roomActivityNotificationService.createSinceLastRun(now);
            dispatch(notificationIds);
        } catch (Exception e) {
            log.error("방 활동 알림 처리 실패 - {}", now, e);
        }
    }

    private void dispatch(List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return;
        }
        try {
            notificationSendExecutor.execute(() -> notificationSendService.send(notificationIds));
        } catch (RejectedExecutionException e) {
            log.warn("알림 발송 작업 거부 - {}건은 복구 스케줄러가 발송", notificationIds.size());
        }
    }
}
