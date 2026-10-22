package com.ringout.api.notification.sender;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final String DEEPLINK_KEY = "deeplink";

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public List<Boolean> send(List<PushMessage> messages) {
        List<Boolean> results = new ArrayList<>();
        for (int from = 0; from < messages.size(); from += MAX_BATCH_SIZE) {
            int to = Math.min(from + MAX_BATCH_SIZE, messages.size());
            results.addAll(sendBatch(messages.subList(from, to)));
        }
        return results;
    }

    private List<Boolean> sendBatch(List<PushMessage> messages) {
        List<Message> fcmMessages = messages.stream()
            .map(this::toFcmMessage)
            .toList();
        try {
            BatchResponse response = firebaseMessaging.sendEach(fcmMessages);
            return response.getResponses().stream()
                .map(this::toResult)
                .toList();
        } catch (FirebaseMessagingException e) {
            log.error("FCM 일괄 발송 실패 - {}건", messages.size(), e);
            return Collections.nCopies(messages.size(), false);
        }
    }

    private boolean toResult(SendResponse response) {
        if (!response.isSuccessful()) {
            log.warn("FCM 발송 실패 - {}", response.getException().getMessagingErrorCode());
        }
        return response.isSuccessful();
    }

    private Message toFcmMessage(PushMessage message) {
        Message.Builder builder = Message.builder()
            .setToken(message.token())
            .setNotification(com.google.firebase.messaging.Notification.builder()
                .setTitle(message.title())
                .setBody(message.body())
                .build());
        if (message.deeplink() != null) {
            builder.putData(DEEPLINK_KEY, message.deeplink());
        }
        return builder.build();
    }
}
