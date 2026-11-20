package com.ringout.api.forest.service;

import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.domain.ForestArrivalDays;
import com.ringout.api.forest.domain.ForestGrowth;
import com.ringout.api.forest.domain.ForestOnboarding;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.response.ForestGrowthResponse;
import com.ringout.api.forest.dto.response.ForestOnboardingCompleteResult;
import com.ringout.api.forest.dto.response.ForestOnboardingResponse;
import com.ringout.api.forest.dto.response.ForestResponse;
import com.ringout.api.forest.dto.response.ForestTreeResponse;
import com.ringout.api.forest.repository.ForestOnboardingRepository;
import com.ringout.api.forest.repository.ForestTreeRepository;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForestService {

    private final ForestOnboardingRepository forestOnboardingRepository;
    private final ForestTreeRepository forestTreeRepository;
    private final AlarmOccurrenceRepository alarmOccurrenceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ForestResponse getForest(Long userId) {
        validateAuthenticatedUserExists(userId);

        ForestArrivalDays arrivalDays = ForestArrivalDays.from(
            alarmOccurrenceRepository.findActiveEndedAtsByUserIdAndEndType(userId, OccurrenceEndType.ARRIVED));
        ForestGrowthResponse growth = ForestGrowthResponse.from(ForestGrowth.of(arrivalDays.count()));
        OffsetDateTime onboardingCompletedAt = findOnboardingCompletedAt(userId);
        List<ForestTreeResponse> trees = forestTreeRepository.findAllByUserIdOrderByGrowthCycle(userId).stream()
            .map(tree -> ForestTreeResponse.of(tree, clock.getZone()))
            .toList();

        return new ForestResponse(growth, onboardingCompletedAt, trees);
    }

    @Transactional
    public ForestOnboardingCompleteResult completeOnboarding(Long userId, ForestOnboardingCompleteRequest request) {
        validateOnboardingRequest(request);
        User user = findAuthenticatedUserForUpdate(userId);

        Optional<ForestOnboarding> savedOnboarding = forestOnboardingRepository.findByUserId(userId);
        if (savedOnboarding.isPresent()) {
            return new ForestOnboardingCompleteResult(toResponse(savedOnboarding.get()), false);
        }

        ForestOnboarding onboarding = forestOnboardingRepository.save(
            ForestOnboarding.complete(user, toServerDateTime(request.completedAt())));
        log.atInfo()
            .addKeyValue("event", "forest_onboarding_completed")
            .addKeyValue("userId", userId)
            .log("숲 온보딩 완료");

        return new ForestOnboardingCompleteResult(toResponse(onboarding), true);
    }

    private void validateOnboardingRequest(ForestOnboardingCompleteRequest request) {
        if (request == null || request.completedAt() == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_ONBOARDING_REQUEST_INVALID);
        }
    }

    private void validateAuthenticatedUserExists(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new GeneralException(ForestErrorStatus.FOREST_UNAUTHORIZED);
        }
    }

    private OffsetDateTime findOnboardingCompletedAt(Long userId) {
        Optional<ForestOnboarding> onboarding = forestOnboardingRepository.findByUserId(userId);
        if (onboarding.isEmpty()) {
            return null;
        }
        return toResponse(onboarding.get()).onboardingCompletedAt();
    }

    private User findAuthenticatedUserForUpdate(Long userId) {
        if (userId == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_UNAUTHORIZED);
        }
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new GeneralException(ForestErrorStatus.FOREST_UNAUTHORIZED));
    }

    private LocalDateTime toServerDateTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private ForestOnboardingResponse toResponse(ForestOnboarding onboarding) {
        return ForestOnboardingResponse.of(onboarding, clock.getZone());
    }
}
