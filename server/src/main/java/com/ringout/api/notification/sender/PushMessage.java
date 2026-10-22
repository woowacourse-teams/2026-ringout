package com.ringout.api.notification.sender;

import java.util.Map;

public record PushMessage(String token, String title, String body, Map<String, String> data) {
}
