package com.ringout.api.notification.sender;

import java.util.List;

public interface PushSender {

    // messages 와 같은 순서로 발송 결과를 돌려준다.
    List<SendResult> send(List<PushMessage> messages);
}
