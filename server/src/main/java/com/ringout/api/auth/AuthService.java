package com.ringout.api.auth;

import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.auth.dto.SignupResponse;
import com.ringout.api.auth.social.SocialLoginClient;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.social.SocialUserInfo;
import com.ringout.api.auth.status.AuthErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.repository.MemberRepository;
import com.ringout.api.member.service.MemberService;
import com.ringout.api.member.status.UserErrorStatus;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.service.TermsService;
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
    private final JwtProvider jwtProvider;
    private final MemberService memberService;
    private final TermsService termsService;

    public AuthService(
        List<SocialLoginClient> loginClients,
        MemberRepository memberRepository,
        JwtProvider jwtProvider,
        MemberService memberService,
        TermsService termsService
    ) {
        this.loginClients = new EnumMap<>(SocialProvider.class);
        loginClients.forEach(client ->
            this.loginClients.put(client.supports(), client)
        );
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.memberService = memberService;
        this.termsService = termsService;
    }

    @Transactional
    public SignupResponse signup(String signupToken, TermsAgreeRequest request) {
        validateSignupToken(signupToken);

        SocialProvider socialProvider = jwtProvider.getSocialProvider(signupToken);
        String providerId = jwtProvider.getProviderId(signupToken);
        String email = jwtProvider.getEmail(signupToken);

        Member member = memberService.register(
            socialProvider,
            providerId,
            email,
            LocalDateTime.now()
        );
        termsService.termsAgree(member.getId(), request);

        String accessToken = jwtProvider.createAccessToken(
            member.getId(),
            member.getSocialProviderId(),
            member.getRole()
        );
        String refreshToken = jwtProvider.createRefreshToken(
            member.getId(),
            member.getSocialProviderId(),
            member.getRole()
        );
        return new SignupResponse(accessToken, refreshToken);
    }

    private void validateSignupToken(String signupToken) {
        if (!jwtProvider.isValid(signupToken) || !jwtProvider.isSignupToken(signupToken)) {
            throw new GeneralException(AuthErrorStatus.UNAUTHORIZED);
        }
    }

    @Transactional(readOnly = true)
    public ReissueResponse reissue(String refreshToken) {
        validateRefreshToken(refreshToken);

        Long memberId = jwtProvider.getUserId(refreshToken);
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        String reissuedAccessToken = jwtProvider.createAccessToken(
            member.getId(),
            member.getSocialProviderId(),
            member.getRole()
        );

        String reissuedRefreshToken = jwtProvider.createRefreshToken(
            member.getId(),
            member.getSocialProviderId(),
            member.getRole()
        );

        return ReissueResponse.of(reissuedAccessToken, reissuedRefreshToken);
    }

    private void validateRefreshToken(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new GeneralException(AuthErrorStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public LoginResponse login(SocialProvider provider, String socialAccessToken) {
        if (socialAccessToken == null || socialAccessToken.isBlank()) {
            throw new IllegalArgumentException("소셜 액세스 토큰은 비어 있을 수 없습니다.");
        }

        SocialLoginClient loginClient = loginClients.get(provider);

        if (loginClient == null) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        SocialUserInfo socialUserInfo = loginClient.authenticate(socialAccessToken);
        LocalDateTime loginAt = LocalDateTime.now();

        return memberRepository
            .findBySocialProviderAndSocialProviderId(
                socialUserInfo.provider(),
                socialUserInfo.providerId()
            )
            .map(member -> loginExistingMember(member, socialUserInfo, loginAt))
            .orElseGet(() -> LoginResponse.signupRequired(
                jwtProvider.createSignupToken(
                    socialUserInfo.provider(),
                    socialUserInfo.providerId(),
                    socialUserInfo.email()
                )
            ));
    }

    private LoginResponse loginExistingMember(
        Member member,
        SocialUserInfo socialUserInfo,
        LocalDateTime loginAt
    ) {
        member.login(loginAt, socialUserInfo.email());
        String accessToken = jwtProvider.createAccessToken(
            member.getId(),
            member.getSocialProviderId(),
            member.getRole()
        );
        String refreshToken = jwtProvider.createRefreshToken(
            member.getId(),
            member.getSocialProviderId(),
            member.getRole()
        );
        return LoginResponse.loggedIn(accessToken, refreshToken);
    }
}
