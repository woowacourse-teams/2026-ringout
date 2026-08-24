package com.ringout.api.member.service;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.dto.request.UpdateNicknameRequest;
import com.ringout.api.member.dto.response.UpdateNicknameResponse;
import com.ringout.api.member.dto.response.UserResponse;
import com.ringout.api.member.repository.MemberRepository;
import com.ringout.api.member.status.UserErrorStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        return new UserResponse(member.getNickname().getValue(), member.getEmail());
    }

    @Transactional
    public Member register(
        SocialProvider socialProvider,
        String providerId,
        String email,
        LocalDateTime registeredAt
    ) {
        if (memberRepository.findBySocialProviderAndSocialProviderId(socialProvider, providerId)
            .isPresent()) {
            throw new GeneralException(UserErrorStatus.USER_ALREADY_EXISTS);
        }

        return memberRepository.save(
            Member.register(socialProvider, providerId, email, registeredAt)
        );
    }

    @Transactional
    public UpdateNicknameResponse updateNickname(
        Long memberId,
        UpdateNicknameRequest request
    ) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        member.changeNickname(request.nickname());

        return new UpdateNicknameResponse(member.getNickname().getValue());
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        memberRepository.delete(member);
    }
}
