package com.ringout.api.member.service;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.member.domain.Member;
import com.ringout.api.member.domain.MemberRepository;
import com.ringout.api.member.dto.request.UpdateNicknameRequest;
import com.ringout.api.member.dto.response.UpdateNicknameResponse;
import com.ringout.api.stamp.repository.StampRepository;
import com.ringout.api.terms.repository.MemberAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberService {

  private final MemberRepository memberRepository;
  private final StampRepository stampRepository;
  private final DestinationRepository destinationRepository;
  private final MemberAgreementRepository memberAgreementRepository;

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

  @Transactional
  public UpdateNicknameResponse updateNickname(
      Long memberId,
      UpdateNicknameRequest request
  ) {
    Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

    member.changeNickname(request.nickname());

    return new UpdateNicknameResponse(member.getNickname().getValue());
  }

  @Transactional
  public void withdraw(Long memberId) {
    Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

    stampRepository.deleteAllByMemberId(memberId);
    destinationRepository.deleteAllByUserId(memberId);
    memberAgreementRepository.deleteAllByMemberId(memberId);
    memberRepository.delete(member);
  }
}
