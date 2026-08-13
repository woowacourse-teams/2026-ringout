package com.ringout.api.auth;

import com.ringout.api.auth.social.SocialLoginClient;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.social.SocialUserInfo;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.domain.MemberRepository;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final Map<SocialProvider, SocialLoginClient> loginClients;
    private final MemberRepository memberRepository;

    public AuthService(List<SocialLoginClient> loginClients, MemberRepository memberRepository) {
        this.loginClients = new EnumMap<>(SocialProvider.class);
        loginClients.forEach(client ->
                this.loginClients.put(client.supports(), client)
        );
        this.memberRepository = memberRepository;
    }

    @Transactional
    public SocialUserInfo login(SocialProvider provider, String socialAccessToken) {
        if (socialAccessToken == null || socialAccessToken.isBlank()) {
            throw new IllegalArgumentException("소셜 액세스 토큰은 비어 있을 수 없습니다.");
        }

        SocialLoginClient loginClient = loginClients.get(provider);

        if (loginClient == null) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        SocialUserInfo socialUserInfo = loginClient.authenticate(socialAccessToken);
        LocalDateTime loginAt = LocalDateTime.now();

        Member member = memberRepository
                .findBySocialProviderAndSocialProviderId(
                        socialUserInfo.provider(),
                        socialUserInfo.providerId()
                )
                .orElseGet(() -> memberRepository.save(
                        Member.register(
                                socialUserInfo.provider(),
                                socialUserInfo.providerId(),
                                socialUserInfo.email(),
                                loginAt
                        )
                ));

        member.login(loginAt, socialUserInfo.email());
        return socialUserInfo;
    }
}
