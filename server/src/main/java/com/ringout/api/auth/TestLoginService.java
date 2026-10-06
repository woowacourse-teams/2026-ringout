package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.status.AuthErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "app.test-login",
    name = "enabled",
    havingValue = "true"
)
public class TestLoginService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final String expectedSecret;
    private final Set<String> allowedProviderIds;
    private final boolean configured;

    public TestLoginService(
        AuthService authService,
        @Value("${app.test-login.secret:}") String expectedSecret,
        @Value("${app.test-login.allowed-provider-ids:}") String allowedProviderIds
    ) {
        this.authService = authService;
        this.expectedSecret = expectedSecret;
        this.allowedProviderIds = parseAllowedProviderIds(allowedProviderIds);
        this.configured = !expectedSecret.isBlank() && !this.allowedProviderIds.isEmpty();

        if (!configured) {
            log.atError()
                .addKeyValue("event", "test_login_configuration_invalid")
                .log("테스트 로그인이 활성화됐지만 secret 또는 허용 계정 설정이 없습니다.");
        }
    }

    public LoginResponse login(String authorization, String testUserId) {
        validateConfiguration();
        validateSecret(authorization);
        validateAllowedUser(testUserId);
        return authService.loginTestUser(testUserId);
    }

    private Set<String> parseAllowedProviderIds(String allowedProviderIds) {
        return Arrays.stream(allowedProviderIds.split(","))
            .map(String::trim)
            .filter(providerId -> !providerId.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private void validateConfiguration() {
        if (!configured) {
            throw new GeneralException(AuthErrorStatus.TEST_LOGIN_UNAVAILABLE);
        }
    }

    private void validateSecret(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw unauthorized();
        }

        String actualSecret = authorization.substring(BEARER_PREFIX.length());
        boolean matches = MessageDigest.isEqual(
            expectedSecret.getBytes(StandardCharsets.UTF_8),
            actualSecret.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw unauthorized();
        }
    }

    private void validateAllowedUser(String testUserId) {
        if (testUserId == null || !allowedProviderIds.contains(testUserId)) {
            throw unauthorized();
        }
    }

    private GeneralException unauthorized() {
        return new GeneralException(AuthErrorStatus.UNAUTHORIZED);
    }
}
