package com.ringout.api.notification.service;

import static java.util.stream.Collectors.partitioningBy;

import com.ringout.api.notification.domain.Campaign;
import com.ringout.api.notification.domain.Notification;
import com.ringout.api.notification.domain.NotificationStatus;
import com.ringout.api.notification.repository.NotificationRepository;
import com.ringout.api.notification.sender.PushMessage;
import com.ringout.api.notification.sender.PushSender;
import com.ringout.api.notification.sender.SendResult;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSendService {

    public static final Duration SENDING_LEASE = Duration.ofMinutes(5);
    private static final Duration PENDING_GRACE = Duration.ofMinutes(1);
    private static final Duration EXPIRY = Duration.ofMinutes(10);
    private static final int RECOVERY_BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;
    private final Clock clock;

    public void send(List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String claimToken = UUID.randomUUID().toString();
        int claimed = notificationRepository.claim(notificationIds, claimToken, now, now.minus(SENDING_LEASE));
        if (claimed == 0) {
            log.info("알림 발송 생략 - {}건 모두 이미 선점됨", notificationIds.size());
            return;
        }

        List<Notification> notifications =
            notificationRepository.findAllWithDeviceAndCampaignByClaimToken(claimToken);
        Map<Boolean, List<Notification>> byToken = notifications.stream()
            .collect(partitioningBy(notification -> notification.getDevice().hasToken()));
        List<Notification> deliverable = byToken.get(true);
        List<Long> tokenlessIds = toIds(byToken.get(false));
        updateStatus(tokenlessIds, NotificationStatus.FAILED, claimToken);

        if (deliverable.isEmpty()) {
            log.info("알림 발송 생략 - 토큰 없음 {}건", tokenlessIds.size());
            return;
        }

        List<PushMessage> messages = deliverable.stream()
            .map(this::toPushMessage)
            .toList();
        List<SendResult> results = pushSender.send(messages);

        Map<SendResult, List<Long>> idsByResult = new EnumMap<>(SendResult.class);
        for (int i = 0; i < deliverable.size(); i++) {
            idsByResult.computeIfAbsent(results.get(i), result -> new ArrayList<>())
                .add(deliverable.get(i).getId());
        }
        List<Long> sentIds = idsByResult.getOrDefault(SendResult.SENT, List.of());
        List<Long> retryIds = idsByResult.getOrDefault(SendResult.RETRYABLE, List.of());
        List<Long> failedIds = idsByResult.getOrDefault(SendResult.PERMANENT, List.of());
        updateStatus(sentIds, NotificationStatus.SENT, claimToken);
        // 다음 복구 스케줄러가 다시 집는다. 재시도 한도는 만료 시한(EXPIRY)이 대신한다.
        updateStatus(retryIds, NotificationStatus.PENDING, claimToken);
        updateStatus(failedIds, NotificationStatus.FAILED, claimToken);
        log.info("알림 발송 완료 - 성공 {}건, 재시도 {}건, 실패 {}건, 토큰 없음 {}건",
            sentIds.size(), retryIds.size(), failedIds.size(), tokenlessIds.size());
    }

    public void recover() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = notificationRepository.expire(now.minus(EXPIRY));
        if (expired > 0) {
            log.warn("발송 시한이 지난 알림 만료 처리 - {}건", expired);
        }

        List<Long> ids = notificationRepository.findRecoverableIds(
            now.minus(EXPIRY), now.minus(PENDING_GRACE), now.minus(SENDING_LEASE),
            Limit.of(RECOVERY_BATCH_SIZE));
        if (ids.isEmpty()) {
            return;
        }
        log.warn("미발송 알림 복구 시도 - {}건", ids.size());
        send(ids);
    }

    private List<Long> toIds(List<Notification> notifications) {
        return notifications.stream()
            .map(Notification::getId)
            .toList();
    }

    private PushMessage toPushMessage(Notification notification) {
        Campaign campaign = notification.getCampaign();
        return new PushMessage(notification.getDevice().getFcmToken(),
            campaign.getTitle(), campaign.getBody(), campaign.getData(), "campaign-" + campaign.getId());
    }

    private void updateStatus(List<Long> ids, NotificationStatus status, String claimToken) {
        if (ids.isEmpty()) {
            return;
        }
        int updated = notificationRepository.updateStatus(ids, status, claimToken);
        if (updated < ids.size()) {
            log.warn("선점을 잃어 상태를 기록하지 못함 - {}건 중 {}건", ids.size(), ids.size() - updated);
        }
    }
}
