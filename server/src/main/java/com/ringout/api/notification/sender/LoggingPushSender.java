package com.ringout.api.notification.sender;

import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    @Override
    public List<Boolean> send(List<PushMessage> messages) {
        log.info("Firebase 비활성화 - 푸시 {}건을 발송하지 않고 성공 처리", messages.size());
        return Collections.nCopies(messages.size(), true);
    }
}
