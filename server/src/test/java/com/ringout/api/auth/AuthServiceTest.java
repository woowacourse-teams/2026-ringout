package com.ringout.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ringout.api.auth.dto.ReissueResponse;
import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.domain.Role;
import com.ringout.api.member.repository.MemberRepository;
import com.ringout.api.member.service.MemberService;
import com.ringout.api.terms.service.TermsService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
        authService = new AuthService(List.of(), memberRepository, jwtProvider, memberService, termsService);
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
            .isEqualTo(ErrorStatus._UNAUTHORIZED);
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
            .isEqualTo(ErrorStatus._UNAUTHORIZED);
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
            .isEqualTo(ErrorStatus.MEMBER_NOT_FOUND);
        assertThat(ErrorStatus.MEMBER_NOT_FOUND.getCode()).isEqualTo("USER404");
        assertThat(ErrorStatus.MEMBER_NOT_FOUND.getMessage()).isEqualTo("존재하지 않는 사용자입니다.");
    }
}
