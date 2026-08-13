package com.ringout.api.auth.social;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoLoginClient implements SocialLoginClient {

    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient = RestClient.create();

    @Override
    public SocialProvider supports() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialUserInfo authenticate(String token) {
        KakaoUserResponse response = restClient.get()
                .uri(USER_INFO_URL)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(KakaoUserResponse.class);

        if (response == null || response.id() == null) {
            throw new IllegalArgumentException("카카오 사용자 정보를 가져올 수 없습니다.");
        }

        String email = response.kakaoAccount() == null
                ? null
                : response.kakaoAccount().email();

        return new SocialUserInfo(
                SocialProvider.KAKAO,
                response.id().toString(),
                email
        );
    }

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
    }

    private record KakaoAccount(String email) {
    }
}
