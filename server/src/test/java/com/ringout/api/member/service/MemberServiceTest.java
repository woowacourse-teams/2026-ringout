package com.ringout.api.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.dto.response.UserResponse;
import com.ringout.api.member.repository.MemberRepository;
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
            .isEqualTo(ErrorStatus.MEMBER_NOT_FOUND);
    }
}
