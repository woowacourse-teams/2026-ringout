package com.ringout.api.notification.sender;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidConfig.Priority;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

    private static final int MAX_BATCH_SIZE = 500;
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Set<MessagingErrorCode> RETRYABLE_ERRORS = EnumSet.of(
        MessagingErrorCode.UNAVAILABLE, MessagingErrorCode.INTERNAL, MessagingErrorCode.QUOTA_EXCEEDED);

    private final FirebaseMessaging firebaseMessaging;
    private final Clock clock;

    @Override
    public List<SendResult> send(List<PushMessage> messages) {
        List<SendResult> results = new ArrayList<>();
        for (int from = 0; from < messages.size(); from += MAX_BATCH_SIZE) {
            int to = Math.min(from + MAX_BATCH_SIZE, messages.size());
            results.addAll(sendBatch(messages.subList(from, to)));
        }
        return results;
    }

    private List<SendResult> sendBatch(List<PushMessage> messages) {
        List<SendResult> results = new ArrayList<>(Collections.nCopies(messages.size(), SendResult.PERMANENT));
        List<Integer> sentIndexes = new ArrayList<>();
        List<Message> fcmMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            try {
                fcmMessages.add(toFcmMessage(messages.get(i)));
                sentIndexes.add(i);
            } catch (IllegalArgumentException e) {
                log.warn("FCM 메시지 생성 실패 - {}", e.getMessage());
            }
        }
        if (fcmMessages.isEmpty()) {
            return results;
        }

        try {
            List<SendResponse> responses = firebaseMessaging.sendEach(fcmMessages).getResponses();
            for (int i = 0; i < responses.size(); i++) {
                results.set(sentIndexes.get(i), toResult(responses.get(i)));
            }
        } catch (FirebaseMessagingException e) {
            // 요청이 FCM 에 닿았는지 알 수 없다. 누락보다 중복을 택해 재시도한다.
            log.error("FCM 일괄 발송 실패 - {}건", fcmMessages.size(), e);
            sentIndexes.forEach(index -> results.set(index, SendResult.RETRYABLE));
        }
        return results;
    }

    private SendResult toResult(SendResponse response) {
        if (response.isSuccessful()) {
            return SendResult.SENT;
        }
        MessagingErrorCode errorCode = response.getException().getMessagingErrorCode();
        log.warn("FCM 발송 실패 - {}", errorCode);
        return RETRYABLE_ERRORS.contains(errorCode) ? SendResult.RETRYABLE : SendResult.PERMANENT;
    }

    private Message toFcmMessage(PushMessage message) {
        Message.Builder builder = Message.builder()
            .setToken(message.token())
            .setNotification(com.google.firebase.messaging.Notification.builder()
                .setTitle(message.title())
                .setBody(message.body())
                .build())
            .setAndroidConfig(androidConfig(message))
            .setApnsConfig(apnsConfig(message));
        if (message.data() != null) {
            builder.putAllData(message.data());
        }
        return builder.build();
    }

    private AndroidConfig androidConfig(PushMessage message) {
        AndroidConfig.Builder builder = AndroidConfig.builder()
            .setPriority(Priority.HIGH)
            .setTtl(TTL.toMillis());
        if (message.notificationKey() != null) {
            // tag: 기기 알림창에서 같은 tag 의 알림을 교체한다.
            // collapseKey: 기기가 오프라인일 때 FCM 이 같은 키의 메시지 중 마지막 것만 보관한다.
            builder.setCollapseKey(message.notificationKey())
                .setNotification(AndroidNotification.builder()
                    .setTag(message.notificationKey())
                    .build());
        }
        return builder.build();
    }

    private ApnsConfig apnsConfig(PushMessage message) {
        ApnsConfig.Builder builder = ApnsConfig.builder()
            .putHeader("apns-priority", "10")
            .putHeader("apns-push-type", "alert")
            .putHeader("apns-expiration", apnsExpiration())
            .setAps(Aps.builder()
                .putCustomData("interruption-level", "time-sensitive")
                .build());
        if (message.notificationKey() != null) {
            // 같은 apns-collapse-id 의 알림은 기기에서 하나로 합쳐 표시된다.
            builder.putHeader("apns-collapse-id", message.notificationKey());
        }
        return builder.build();
    }

    private String apnsExpiration() {
        return String.valueOf(Instant.now(clock).plus(TTL).getEpochSecond());
    }
}
