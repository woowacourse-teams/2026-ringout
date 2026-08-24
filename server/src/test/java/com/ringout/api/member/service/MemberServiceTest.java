package com.ringout.api.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.dto.request.UpdateNicknameRequest;
import com.ringout.api.member.dto.response.UpdateNicknameResponse;
import com.ringout.api.member.dto.response.UserResponse;
import com.ringout.api.member.repository.MemberRepository;
import com.ringout.api.member.status.UserErrorStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository);
    }

    @Test
    void 회원_정보를_조회한다() {
        // given
        Long memberId = 1L;
        Member member = Member.register(
            SocialProvider.KAKAO,
            "kakao-id",
            "uio6699@naver.com",
            LocalDateTime.of(2026, 8, 19, 10, 0)
        );
        member.changeNickname("서여");
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        // when
        UserResponse response = memberService.getUser(memberId);

        // then
        assertThat(response.nickname()).isEqualTo("서여");
        assertThat(response.email()).isEqualTo("uio6699@naver.com");
    }

    @Test
    void 존재하지_않는_회원이면_USER404_예외가_발생한다() {
        // given
        Long memberId = 1L;
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> memberService.getUser(memberId))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);
    }

    @Test
    void 회원의_닉네임을_수정한다() {
        Long memberId = 1L;
        Member member = Member.register(
            SocialProvider.KAKAO,
            "provider-id",
            "member@example.com",
            LocalDateTime.now()
        );
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        UpdateNicknameResponse response = memberService.updateNickname(
            memberId,
            new UpdateNicknameRequest("새로운닉네임")
        );

        assertThat(response.nickname()).isEqualTo("새로운닉네임");
        assertThat(member.getNickname().getValue()).isEqualTo("새로운닉네임");
    }

    @Test
    void 존재하지_않는_회원의_닉네임은_수정할_수_없다() {
        Long memberId = 1L;
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateNickname(
            memberId,
            new UpdateNicknameRequest("새로운닉네임")
        ))
            .isInstanceOf(GeneralException.class)
            .extracting("code")
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);

    }

    @Test
    void 허용되지_않은_형식의_닉네임은_수정할_수_없다() {
        Long memberId = 1L;
        Member member = Member.register(
            SocialProvider.KAKAO,
            "provider-id",
            "member@example.com",
            LocalDateTime.now()
        );
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.updateNickname(
            memberId,
            new UpdateNicknameRequest("잘못된_닉네임")
        ))
            .isInstanceOf(GeneralException.class)
            .extracting("code")
            .isEqualTo(UserErrorStatus.USER_NICKNAME_INVALID_FORMAT);
    }

    @Test
    void 회원을_탈퇴한다() {
        Long memberId = 1L;
        Member member = Member.register(
            SocialProvider.KAKAO,
            "provider-id",
            "member@example.com",
            LocalDateTime.now()
        );
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        memberService.withdraw(memberId);

        verify(memberRepository).delete(member);
    }

    @Test
    void 존재하지_않는_회원은_탈퇴할_수_없다() {
        Long memberId = 1L;
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.withdraw(memberId))
            .isInstanceOf(GeneralException.class)
            .extracting("code")
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);
    }
}
