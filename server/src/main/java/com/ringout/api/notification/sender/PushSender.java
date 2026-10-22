package com.ringout.api.notification.sender;

import java.util.List;

public interface PushSender {

    // messages 와 같은 순서로 성공 여부를 돌려준다.
    List<Boolean> send(List<PushMessage> messages);
}
