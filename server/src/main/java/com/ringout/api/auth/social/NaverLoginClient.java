package com.ringout.api.auth.social;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NaverLoginClient implements SocialLoginClient {

    private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient = RestClient.create();

    @Override
    public SocialProvider supports() {
        return SocialProvider.NAVER;
    }

    @Override
    public SocialUserInfo authenticate(String token) {
        NaverUserResponse response = restClient.get()
                .uri(USER_INFO_URL)
                .headers((headers -> headers.setBearerAuth(token)))
                .retrieve()
                .body(NaverUserResponse.class);

        if (response == null || response.response() == null || response.response().id() == null) {
            throw new IllegalArgumentException("네이버 사용자 정보를 가져올 수 없습니다.");
        }

        return new SocialUserInfo(
                SocialProvider.NAVER,
                response.response().id(),
                response.response().email()
        );
    }

    private record NaverUserResponse(NaverProfile response) {
    }

    private record NaverProfile(String id, String email) {
    }
}
