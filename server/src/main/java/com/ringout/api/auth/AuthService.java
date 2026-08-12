package com.ringout.api.auth;

import com.ringout.api.auth.social.SocialLoginClient;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.social.SocialUserInfo;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final Map<SocialProvider, SocialLoginClient> loginClients;

    public AuthService(List<SocialLoginClient> loginClients) {
        this.loginClients = new EnumMap<>(SocialProvider.class);
        loginClients.forEach(client ->
                this.loginClients.put(client.supports(), client)
        );
    }

    public SocialUserInfo login(SocialProvider provider, String socialAccessToken) {
        if (socialAccessToken == null || socialAccessToken.isBlank()) {
            throw new IllegalArgumentException("소셜 액세스 토큰은 비어 있을 수 없습니다.");
        }

        SocialLoginClient loginClient = loginClients.get(provider);

        if (loginClient == null) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        return loginClient.authenticate(socialAccessToken);
    }
}
