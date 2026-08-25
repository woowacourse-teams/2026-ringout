package com.ringout.api.user.repository;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findBySocialProviderAndSocialProviderId(
        SocialProvider socialProvider,
        String socialProviderId
    );
}
