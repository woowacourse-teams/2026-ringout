package com.ringout.api.config.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
@ConditionalOnProperty(
    prefix = "app.firebase",
    name = "enabled",
    havingValue = "true"
)
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (properties.credentialsBase64() == null || properties.credentialsBase64().isBlank()) {
            throw new IllegalStateException("Firebase가 활성화됐지만 서비스 계정 키(FIREBASE_CREDENTIALS_BASE64)가 없습니다.");
        }

        byte[] credentials = Base64.getDecoder().decode(properties.credentialsBase64().trim());
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(credentials)))
            .build();

        return FirebaseApp.getApps().isEmpty()
            ? FirebaseApp.initializeApp(options)
            : FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
