package com.ringout.api.notification.sender;

import java.util.Map;

// notificationKey 가 같은 알림은 기기에서 새로 쌓이지 않고 앞의 알림을 교체한다.
public record PushMessage(String token, String title, String body, Map<String, String> data, String notificationKey) {
}
