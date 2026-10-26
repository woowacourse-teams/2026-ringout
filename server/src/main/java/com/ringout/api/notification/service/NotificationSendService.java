package com.ringout.api.notification.service;

import com.ringout.api.notification.domain.Campaign;
import com.ringout.api.notification.domain.Notification;
import com.ringout.api.notification.domain.NotificationStatus;
import com.ringout.api.notification.repository.NotificationRepository;
import com.ringout.api.notification.sender.PushMessage;
import com.ringout.api.notification.sender.PushSender;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSendService {

    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;

    public void send(List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = notificationRepository.findAllWithDeviceAndCampaignByIdIn(notificationIds);
        List<PushMessage> messages = notifications.stream()
            .map(this::toPushMessage)
            .toList();
        List<Boolean> results = pushSender.send(messages);

        List<Long> sentIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        for (int i = 0; i < notifications.size(); i++) {
            Long id = notifications.get(i).getId();
            if (results.get(i)) {
                sentIds.add(id);
            } else {
                failedIds.add(id);
            }
        }
        updateStatus(sentIds, NotificationStatus.SENT);
        updateStatus(failedIds, NotificationStatus.FAILED);
        log.info("알림 발송 완료 - 성공 {}건, 실패 {}건", sentIds.size(), failedIds.size());
    }

    private PushMessage toPushMessage(Notification notification) {
        Campaign campaign = notification.getCampaign();
        return new PushMessage(notification.getDevice().getFcmToken(),
            campaign.getTitle(), campaign.getBody(), campaign.getData());
    }

    private void updateStatus(List<Long> ids, NotificationStatus status) {
        if (!ids.isEmpty()) {
            notificationRepository.updateStatus(ids, status);
        }
    }
}
