package com.ringout.api.notification.sender;

public record PushMessage(String token, String title, String body, String deeplink) {
}
