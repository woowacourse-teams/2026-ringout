package com.ringout.api.config.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.firebase")
public record FirebaseProperties(
    boolean enabled,
    String credentialsBase64
) {
}
