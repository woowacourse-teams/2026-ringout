package com.ringout.api.member.domain;

import com.ringout.api.auth.social.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findBySocialProviderAndSocialProviderId(
      SocialProvider socialProvider,
      String socialProviderId
  );
}
