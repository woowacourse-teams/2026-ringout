package com.ringout.api.member.repository;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findBySocialProviderAndSocialProviderId(
        SocialProvider socialProvider,
        String socialProviderId
    );
}
