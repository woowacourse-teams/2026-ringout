package com.ringout.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.ringout.api.auth.dto.LoginResponse;
import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.auth.social.SocialLoginClient;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.auth.social.SocialUserInfo;
import com.ringout.api.auth.status.AuthErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.domain.Role;
import com.ringout.api.member.repository.MemberRepository;
import com.ringout.api.member.service.MemberService;
import com.ringout.api.member.status.UserErrorStatus;
import com.ringout.api.terms.service.TermsService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SOCIAL_ACCESS_TOKEN = "social-access-token";
    private static final String PROVIDER_ID = "provider-user-id";
    private static final String EMAIL = "member@example.com";

    @Mock
    private SocialLoginClient loginClient;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private MemberService memberService;

    @Mock
    private TermsService termsService;

    private AuthService authService;

    private final String refreshToken = "refresh-token";
    private final Long memberId = 1L;

    @BeforeEach
    void setUp() {
        when(loginClient.supports()).thenReturn(SocialProvider.KAKAO);
        authService = new AuthService(
            List.of(loginClient),
            memberRepository,
            jwtProvider,
            memberService,
            termsService
        );
    }

    @Test
    void 유효한_refreshToken이면_새_토큰_쌍을_발급한다() {
        // given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(memberId);
        given(member.getSocialProviderId()).willReturn("social-provider-id");
        given(member.getRole()).willReturn(Role.USER);

        given(jwtProvider.isValid(refreshToken)).willReturn(true);
        given(jwtProvider.isRefreshToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(memberId);
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(jwtProvider.createAccessToken(memberId, "social-provider-id", Role.USER))
            .willReturn("new-access-token");
        given(jwtProvider.createRefreshToken(memberId, "social-provider-id", Role.USER))
            .willReturn("new-refresh-token");

        // when
        ReissueResponse response = authService.reissue(refreshToken);

        // then
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void 유효하지_않은_토큰이면_예외가_발생한다() {
        // given
        given(jwtProvider.isValid(refreshToken)).willReturn(false);

        // when // then
        assertThatThrownBy(() -> authService.reissue(refreshToken))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(AuthErrorStatus.UNAUTHORIZED);
    }

    @Test
    void refreshToken_타입이_아니면_예외가_발생한다() {
        // given
        given(jwtProvider.isValid(refreshToken)).willReturn(true);
        given(jwtProvider.isRefreshToken(refreshToken)).willReturn(false);

        // when // then
        assertThatThrownBy(() -> authService.reissue(refreshToken))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(AuthErrorStatus.UNAUTHORIZED);
    }

    @Test
    void 존재하지_않는_회원이면_예외가_발생한다() {
        // given
        given(jwtProvider.isValid(refreshToken)).willReturn(true);
        given(jwtProvider.isRefreshToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(memberId);
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> authService.reissue(refreshToken))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);
        assertThat(UserErrorStatus.USER_NOT_FOUND.getCode()).isEqualTo("USER404");
        assertThat(UserErrorStatus.USER_NOT_FOUND.getMessage())
            .isEqualTo("존재하지 않는 사용자입니다.");
    }

    @Test
    void 신규_소셜_사용자는_가입_토큰을_발급한다() {
        SocialUserInfo socialUserInfo = new SocialUserInfo(
            SocialProvider.KAKAO,
            PROVIDER_ID,
            EMAIL
        );
        when(loginClient.authenticate(SOCIAL_ACCESS_TOKEN)).thenReturn(socialUserInfo);
        when(memberRepository.findBySocialProviderAndSocialProviderId(
            SocialProvider.KAKAO,
            PROVIDER_ID
        )).thenReturn(Optional.empty());
        when(jwtProvider.createSignupToken(SocialProvider.KAKAO, PROVIDER_ID, EMAIL))
            .thenReturn("signup-token");

        LoginResponse result = authService.login(
            SocialProvider.KAKAO,
            SOCIAL_ACCESS_TOKEN
        );

        verify(memberRepository, never()).save(any(Member.class));
        assertThat(result.signupToken()).isEqualTo("signup-token");
        assertThat(result.accessToken()).isNull();
        assertThat(result.isNewUser()).isTrue();
    }

    @Test
    void 기존_소셜_사용자는_새로_저장하지_않고_로그인_시각을_갱신한다() {
        LocalDateTime previousLoginAt = LocalDateTime.now().minusDays(1);
        Member member = Member.register(
            SocialProvider.KAKAO,
            PROVIDER_ID,
            "old@example.com",
            previousLoginAt
        );
        SocialUserInfo socialUserInfo = new SocialUserInfo(
            SocialProvider.KAKAO,
            PROVIDER_ID,
            EMAIL
        );
        when(loginClient.authenticate(SOCIAL_ACCESS_TOKEN)).thenReturn(socialUserInfo);
        when(memberRepository.findBySocialProviderAndSocialProviderId(
            SocialProvider.KAKAO,
            PROVIDER_ID
        )).thenReturn(Optional.of(member));
        when(jwtProvider.createAccessToken(any(), any(), any())).thenReturn("access-token");

        LoginResponse result = authService.login(
            SocialProvider.KAKAO,
            SOCIAL_ACCESS_TOKEN
        );

        verify(memberRepository, never()).save(any(Member.class));
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.signupToken()).isNull();
        assertThat(result.isNewUser()).isFalse();
        assertThat(member.getLastLoginAt()).isAfter(previousLoginAt);
        assertThat(member.getLastAccessedAt()).isEqualTo(member.getLastLoginAt());
        assertThat(member.getEmail()).isEqualTo(EMAIL);
    }
}
