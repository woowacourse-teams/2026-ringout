package com.ringout.api.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ringout.api.notification.service.NotificationSendService;
import com.ringout.api.notification.service.RoomActivityNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomActivityNotificationSchedulerTest {

    // 2026-10-19(월) 09:00:00.123 (Asia/Seoul)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T00:00:00.123Z"), ZoneId.of("Asia/Seoul"));

    private final RoomActivityNotificationService roomActivityNotificationService =
        mock(RoomActivityNotificationService.class);
    private final NotificationSendService notificationSendService = mock(NotificationSendService.class);
    private final RoomActivityNotificationScheduler scheduler =
        new RoomActivityNotificationScheduler(roomActivityNotificationService, notificationSendService, CLOCK);

    @Test
    void 현재_시각을_분_단위로_잘라_알림을_생성하고_생성된_알림을_보낸다() {
        // given
        given(roomActivityNotificationService.create(any())).willReturn(List.of(1L, 2L));

        // when
        scheduler.notifyRoomActivity();

        // then
        then(roomActivityNotificationService).should().create(LocalDateTime.of(2026, 10, 19, 9, 0));
        then(notificationSendService).should().send(List.of(1L, 2L));
    }

    @Test
    void 처리_중_예외가_나도_스케줄러_밖으로_던지지_않는다() {
        // given
        given(roomActivityNotificationService.create(any())).willThrow(new IllegalStateException("DB 오류"));

        // when & then
        assertThatCode(scheduler::notifyRoomActivity).doesNotThrowAnyException();
        then(notificationSendService).should(never()).send(anyList());
    }
}
