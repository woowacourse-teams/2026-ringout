package com.ringout.api.member.service;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.domain.MemberRepository;
import com.ringout.api.member.dto.request.UpdateNicknameRequest;
import com.ringout.api.member.dto.response.UpdateNicknameResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberService {

  private final MemberRepository memberRepository;

  @Transactional
  public Member register(
      SocialProvider socialProvider,
      String providerId,
      String email,
      LocalDateTime registeredAt
  ) {
    if (memberRepository.findBySocialProviderAndSocialProviderId(socialProvider, providerId)
        .isPresent()) {
      throw new GeneralException(ErrorStatus.MEMBER_ALREADY_EXISTS);
    }

    return memberRepository.save(
        Member.register(socialProvider, providerId, email, registeredAt)
    );
  }
}
