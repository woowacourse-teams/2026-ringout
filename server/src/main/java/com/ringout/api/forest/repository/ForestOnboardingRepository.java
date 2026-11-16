package com.ringout.api.forest.repository;

import com.ringout.api.forest.domain.ForestOnboarding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForestOnboardingRepository extends JpaRepository<ForestOnboarding, Long> {

    @Query("""
        select onboarding
        from ForestOnboarding onboarding
        where onboarding.user.id = :userId
        """)
    Optional<ForestOnboarding> findByUserId(@Param("userId") Long userId);
}
