package com.ringout.api.auth.social;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleLoginClient implements SocialLoginClient {

    private static final String USER_INFO_URL =
            "https://openidconnect.googleapis.com/v1/userinfo";

    private final RestClient restClient = RestClient.create();

    @Override
    public SocialProvider supports() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialUserInfo authenticate(String token) {
        GoogleUserResponse response = restClient.get()
                .uri(USER_INFO_URL)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(GoogleUserResponse.class);

        if (response == null || response.sub() == null) {
            throw new IllegalArgumentException("구글 사용자 정보를 가져올 수 없습니다.");
        }

        return new SocialUserInfo(
                SocialProvider.GOOGLE,
                response.sub(),
                response.email()
        );
    }

    private record GoogleUserResponse(String sub, String email) {
    }
}
