package com.ringout.api.forest.service;

import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.domain.ForestArrivalDays;
import com.ringout.api.forest.domain.ForestTree;
import com.ringout.api.forest.dto.request.ForestTreeAcknowledgeRequest;
import com.ringout.api.forest.dto.request.ForestTreePlantRequest;
import com.ringout.api.forest.dto.response.ForestTreeAcknowledgeResult;
import com.ringout.api.forest.dto.response.ForestTreePlantResult;
import com.ringout.api.forest.dto.response.ForestTreeResponse;
import com.ringout.api.forest.repository.ForestOnboardingRepository;
import com.ringout.api.forest.repository.ForestTreeRepository;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForestTreeService {

    private final ForestTreeRepository forestTreeRepository;
    private final ForestOnboardingRepository forestOnboardingRepository;
    private final AlarmOccurrenceRepository alarmOccurrenceRepository;
    private final UserRepository userRepository;
    private final ForestTreeSlotAllocator slotAllocator;
    private final Clock clock;

    @Transactional
    public ForestTreePlantResult plantTree(Long userId, String rawGrowthCycle, ForestTreePlantRequest request) {
        int growthCycle = ForestTree.parseGrowthCycle(rawGrowthCycle);
        validatePlantRequest(request);
        User user = findAuthenticatedUserForUpdate(userId);

        Optional<ForestTree> savedTree = forestTreeRepository.findByUserIdAndGrowthCycle(userId, growthCycle);
        if (savedTree.isPresent()) {
            return new ForestTreePlantResult(toResponse(savedTree.get()), false);
        }

        LocalDateTime plantedAt = request.plantedAt().atZoneSameInstant(clock.getZone()).toLocalDateTime();
        validatePlantingMatchesArrivals(userId, growthCycle, plantedAt);

        int slotIndex = slotAllocator.allocate(findOccupiedSlots(userId));
        ForestTree tree = forestTreeRepository.save(
            ForestTree.plant(user, growthCycle, request.kind(), slotIndex, plantedAt));
        log.atInfo()
            .addKeyValue("event", "forest_tree_planted")
            .addKeyValue("userId", userId)
            .addKeyValue("growthCycle", growthCycle)
            .addKeyValue("slotIndex", slotIndex)
            .log("숲 나무 심기");

        return new ForestTreePlantResult(toResponse(tree), true);
    }

    @Transactional
    public ForestTreeAcknowledgeResult acknowledgeTree(Long userId, String rawGrowthCycle,
        ForestTreeAcknowledgeRequest request) {
        int growthCycle = parseAcknowledgeGrowthCycle(rawGrowthCycle);
        validateAcknowledgeRequest(request);
        validateAuthenticatedUserExists(userId);

        ForestTree tree = forestTreeRepository.findByUserIdAndGrowthCycleForUpdate(userId, growthCycle)
            .orElseThrow(() -> new GeneralException(ForestErrorStatus.FOREST_TREE_NOT_FOUND));
        validateOnboardingCompleted(userId);

        boolean acknowledged = tree.acknowledge(
            request.acknowledgedAt().atZoneSameInstant(clock.getZone()).toLocalDateTime());
        if (acknowledged) {
            log.atInfo()
                .addKeyValue("event", "forest_tree_acknowledged")
                .addKeyValue("userId", userId)
                .addKeyValue("growthCycle", growthCycle)
                .log("숲 나무 확인");
        }

        return new ForestTreeAcknowledgeResult(toResponse(tree), acknowledged);
    }

    private int parseAcknowledgeGrowthCycle(String rawGrowthCycle) {
        try {
            return ForestTree.parseGrowthCycle(rawGrowthCycle);
        } catch (GeneralException exception) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID);
        }
    }

    private void validateAcknowledgeRequest(ForestTreeAcknowledgeRequest request) {
        if (request == null || request.acknowledgedAt() == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID);
        }
    }

    private void validateAuthenticatedUserExists(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new GeneralException(ForestErrorStatus.FOREST_UNAUTHORIZED);
        }
    }

    private void validateOnboardingCompleted(Long userId) {
        if (forestOnboardingRepository.findByUserId(userId).isEmpty()) {
            throw new GeneralException(ForestErrorStatus.FOREST_ONBOARDING_NOT_COMPLETED);
        }
    }

    private void validatePlantRequest(ForestTreePlantRequest request) {
        if (request == null || request.kind() == null || request.plantedAt() == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID);
        }
    }

    private void validatePlantingMatchesArrivals(Long userId, int growthCycle, LocalDateTime plantedAt) {
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(
            alarmOccurrenceRepository.findActiveEndedAtsByUserIdAndEndType(userId, OccurrenceEndType.ARRIVED));
        if (!arrivalDays.canPlant(growthCycle, plantedAt.toLocalDate())) {
            log.atWarn()
                .addKeyValue("event", "forest_tree_record_mismatch")
                .addKeyValue("userId", userId)
                .addKeyValue("growthCycle", growthCycle)
                .addKeyValue("arrivalDayCount", arrivalDays.count())
                .log("숲 나무 획득 기록 불일치");
            throw new GeneralException(ForestErrorStatus.FOREST_TREE_RECORD_MISMATCH);
        }
    }

    private User findAuthenticatedUserForUpdate(Long userId) {
        if (userId == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_UNAUTHORIZED);
        }
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new GeneralException(ForestErrorStatus.FOREST_UNAUTHORIZED));
    }

    private Set<Integer> findOccupiedSlots(Long userId) {
        return new HashSet<>(forestTreeRepository.findSlotIndexesByUserId(userId));
    }

    private ForestTreeResponse toResponse(ForestTree tree) {
        return ForestTreeResponse.of(tree, clock.getZone());
    }
}
