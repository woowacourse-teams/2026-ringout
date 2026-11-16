package com.ringout.api.forest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.domain.ForestOnboarding;
import com.ringout.api.forest.domain.ForestTree;
import com.ringout.api.forest.domain.TreeKind;
import com.ringout.api.forest.dto.request.ForestTreeAcknowledgeRequest;
import com.ringout.api.forest.dto.request.ForestTreePlantRequest;
import com.ringout.api.forest.dto.response.ForestTreeAcknowledgeResult;
import com.ringout.api.forest.dto.response.ForestTreePlantResult;
import com.ringout.api.forest.repository.ForestOnboardingRepository;
import com.ringout.api.forest.repository.ForestTreeRepository;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForestTreeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),
        ZoneId.of("Asia/Seoul"));
    private static final LocalDate FIRST_ARRIVED_DAY = LocalDate.of(2026, 11, 1);
    private static final OffsetDateTime FIFTH_DAY_ARRIVED_AT = OffsetDateTime.parse("2026-11-05T07:48:10+09:00");

    @Mock
    private ForestTreeRepository forestTreeRepository;

    @Mock
    private ForestOnboardingRepository forestOnboardingRepository;

    @Mock
    private AlarmOccurrenceRepository alarmOccurrenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ForestTreeSlotAllocator slotAllocator;

    private ForestTreeService forestTreeService;

    @BeforeEach
    void setUp() {
        forestTreeService = new ForestTreeService(forestTreeRepository, forestOnboardingRepository,
            alarmOccurrenceRepository, userRepository, slotAllocator, FIXED_CLOCK);
    }

    @Nested
    class 나무_심기 {

        @Test
        void 획득_자격이_있는_새_회차면_빈_칸을_배정해_저장하고_생성으로_응답한다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            given(forestTreeRepository.findByUserIdAndGrowthCycle(userId, 1)).willReturn(Optional.empty());
            givenArrivedDays(userId, 7);
            given(forestTreeRepository.findSlotIndexesByUserId(userId)).willReturn(List.of(3, 9));
            given(slotAllocator.allocate(Set.of(3, 9))).willReturn(7);
            given(forestTreeRepository.save(any(ForestTree.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            ForestTreePlantResult result = forestTreeService.plantTree(userId, "1",
                new ForestTreePlantRequest(TreeKind.PINE, FIFTH_DAY_ARRIVED_AT));

            // then
            assertThat(result.created()).isTrue();
            assertThat(result.response().growthCycle()).isEqualTo(1);
            assertThat(result.response().kind()).isEqualTo(TreeKind.PINE);
            assertThat(result.response().slotIndex()).isEqualTo(7);
            assertThat(result.response().plantedAt()).isEqualTo(FIFTH_DAY_ARRIVED_AT);
            assertThat(result.response().acknowledgedAt()).isNull();
        }

        @Test
        void 다른_시간대의_획득_시각을_서버_시간대로_변환해_검증하고_저장한다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            given(forestTreeRepository.findByUserIdAndGrowthCycle(userId, 1)).willReturn(Optional.empty());
            givenArrivedDays(userId, 5);
            given(forestTreeRepository.findSlotIndexesByUserId(userId)).willReturn(List.of());
            given(slotAllocator.allocate(Set.of())).willReturn(0);
            given(forestTreeRepository.save(any(ForestTree.class))).willAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<ForestTree> savedTree = ArgumentCaptor.forClass(ForestTree.class);

            // when
            forestTreeService.plantTree(userId, "1",
                new ForestTreePlantRequest(TreeKind.OAK, OffsetDateTime.parse("2026-11-04T22:48:10Z")));

            // then
            verify(forestTreeRepository).save(savedTree.capture());
            assertThat(savedTree.getValue().getPlantedAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 7, 48, 10));
        }

        @Test
        void 같은_회차의_나무가_있으면_변경하지_않고_저장된_나무를_반환한다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            ForestTree savedTree = ForestTree.plant(mock(User.class), 1, TreeKind.OAK, 3,
                LocalDateTime.of(2026, 11, 5, 7, 48, 10));
            given(forestTreeRepository.findByUserIdAndGrowthCycle(userId, 1)).willReturn(Optional.of(savedTree));

            // when
            ForestTreePlantResult result = forestTreeService.plantTree(userId, "1",
                new ForestTreePlantRequest(TreeKind.PINE, FIFTH_DAY_ARRIVED_AT));

            // then
            assertThat(result.created()).isFalse();
            assertThat(result.response().kind()).isEqualTo(TreeKind.OAK);
            assertThat(result.response().slotIndex()).isEqualTo(3);
            verifyNoInteractions(alarmOccurrenceRepository, slotAllocator);
            verify(forestTreeRepository, never()).save(any());
        }

        @Test
        void 성공_날짜_수가_부족하면_FOREST409를_던진다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            given(forestTreeRepository.findByUserIdAndGrowthCycle(userId, 2)).willReturn(Optional.empty());
            givenArrivedDays(userId, 7);

            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(userId, "2",
                new ForestTreePlantRequest(TreeKind.PINE, OffsetDateTime.parse("2026-11-10T07:48:10+09:00"))))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_RECORD_MISMATCH));
            verify(forestTreeRepository, never()).save(any());
        }

        @Test
        void 획득_날짜가_해당_회차의_성공_날짜와_다르면_FOREST409를_던진다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            given(forestTreeRepository.findByUserIdAndGrowthCycle(userId, 1)).willReturn(Optional.empty());
            givenArrivedDays(userId, 7);

            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(userId, "1",
                new ForestTreePlantRequest(TreeKind.PINE, OffsetDateTime.parse("2026-11-06T08:00:00+09:00"))))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_RECORD_MISMATCH));
            verify(forestTreeRepository, never()).save(any());
        }

        @Test
        void 회차가_올바르지_않으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(1L, "17",
                new ForestTreePlantRequest(TreeKind.PINE, FIFTH_DAY_ARRIVED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, alarmOccurrenceRepository);
        }

        @Test
        void 요청_본문이_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(1L, "1", null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, alarmOccurrenceRepository);
        }

        @Test
        void 나무_종류가_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(1L, "1",
                new ForestTreePlantRequest(null, FIFTH_DAY_ARRIVED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, alarmOccurrenceRepository);
        }

        @Test
        void 획득_시각이_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(1L, "1",
                new ForestTreePlantRequest(TreeKind.PINE, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, alarmOccurrenceRepository);
        }

        @Test
        void 인증된_사용자가_존재하지_않으면_FOREST401을_던진다() {
            // given
            given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

            // when // then
            assertThatThrownBy(() -> forestTreeService.plantTree(1L, "1",
                new ForestTreePlantRequest(TreeKind.PINE, FIFTH_DAY_ARRIVED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_UNAUTHORIZED));
            verifyNoInteractions(forestTreeRepository, alarmOccurrenceRepository);
        }
    }

    @Nested
    class 나무_확인 {

        private static final OffsetDateTime ACKNOWLEDGED_AT = OffsetDateTime.parse("2026-11-10T08:01:00+09:00");

        @Test
        void 확인_시각이_없는_나무면_요청_시각을_저장하고_확인으로_응답한다() {
            // given
            Long userId = 1L;
            ForestTree tree = plantedTree();
            given(userRepository.existsById(userId)).willReturn(true);
            given(forestTreeRepository.findByUserIdAndGrowthCycleForUpdate(userId, 1)).willReturn(Optional.of(tree));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.of(completedOnboarding()));

            // when
            ForestTreeAcknowledgeResult result = forestTreeService.acknowledgeTree(userId, "1",
                new ForestTreeAcknowledgeRequest(ACKNOWLEDGED_AT));

            // then
            assertThat(result.acknowledged()).isTrue();
            assertThat(result.response().acknowledgedAt()).isEqualTo(ACKNOWLEDGED_AT);
            assertThat(result.response().slotIndex()).isEqualTo(7);
            assertThat(tree.getAcknowledgedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 8, 1, 0));
        }

        @Test
        void 다른_시간대의_확인_시각을_서버_시간대로_변환해_저장한다() {
            // given
            Long userId = 1L;
            ForestTree tree = plantedTree();
            given(userRepository.existsById(userId)).willReturn(true);
            given(forestTreeRepository.findByUserIdAndGrowthCycleForUpdate(userId, 1)).willReturn(Optional.of(tree));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.of(completedOnboarding()));

            // when
            ForestTreeAcknowledgeResult result = forestTreeService.acknowledgeTree(userId, "1",
                new ForestTreeAcknowledgeRequest(OffsetDateTime.parse("2026-11-09T23:01:00Z")));

            // then
            assertThat(result.response().acknowledgedAt()).isEqualTo(ACKNOWLEDGED_AT);
        }

        @Test
        void 이미_확인한_나무면_변경하지_않고_저장된_시각을_반환한다() {
            // given
            Long userId = 1L;
            ForestTree tree = plantedTree();
            tree.acknowledge(LocalDateTime.of(2026, 11, 10, 8, 1, 0));
            given(userRepository.existsById(userId)).willReturn(true);
            given(forestTreeRepository.findByUserIdAndGrowthCycleForUpdate(userId, 1)).willReturn(Optional.of(tree));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.of(completedOnboarding()));

            // when
            ForestTreeAcknowledgeResult result = forestTreeService.acknowledgeTree(userId, "1",
                new ForestTreeAcknowledgeRequest(OffsetDateTime.parse("2026-11-11T09:00:00+09:00")));

            // then
            assertThat(result.acknowledged()).isFalse();
            assertThat(result.response().acknowledgedAt()).isEqualTo(ACKNOWLEDGED_AT);
        }

        @Test
        void 해당_회차의_나무가_없으면_FOREST404를_던진다() {
            // given
            Long userId = 1L;
            given(userRepository.existsById(userId)).willReturn(true);
            given(forestTreeRepository.findByUserIdAndGrowthCycleForUpdate(userId, 2)).willReturn(Optional.empty());

            // when // then
            assertThatThrownBy(() -> forestTreeService.acknowledgeTree(userId, "2",
                new ForestTreeAcknowledgeRequest(ACKNOWLEDGED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_NOT_FOUND));
            verify(forestTreeRepository, never()).save(any());
        }

        @Test
        void 숲_온보딩을_완료하지_않았으면_FOREST409를_던지고_확인하지_않는다() {
            // given
            Long userId = 1L;
            ForestTree tree = plantedTree();
            given(userRepository.existsById(userId)).willReturn(true);
            given(forestTreeRepository.findByUserIdAndGrowthCycleForUpdate(userId, 1)).willReturn(Optional.of(tree));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.empty());

            // when // then
            assertThatThrownBy(() -> forestTreeService.acknowledgeTree(userId, "1",
                new ForestTreeAcknowledgeRequest(ACKNOWLEDGED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_ONBOARDING_NOT_COMPLETED));
            assertThat(tree.getAcknowledgedAt()).isNull();
        }

        @Test
        void 회차가_올바르지_않으면_나무_확인_요청_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.acknowledgeTree(1L, "17",
                new ForestTreeAcknowledgeRequest(ACKNOWLEDGED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, forestOnboardingRepository);
        }

        @Test
        void 요청_본문이_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.acknowledgeTree(1L, "1", null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, forestOnboardingRepository);
        }

        @Test
        void 확인_시각이_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestTreeService.acknowledgeTree(1L, "1",
                new ForestTreeAcknowledgeRequest(null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestTreeRepository, forestOnboardingRepository);
        }

        @Test
        void 인증된_사용자가_존재하지_않으면_FOREST401을_던진다() {
            // given
            given(userRepository.existsById(1L)).willReturn(false);

            // when // then
            assertThatThrownBy(() -> forestTreeService.acknowledgeTree(1L, "1",
                new ForestTreeAcknowledgeRequest(ACKNOWLEDGED_AT)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_UNAUTHORIZED));
            verifyNoInteractions(forestTreeRepository, forestOnboardingRepository);
        }

        private ForestTree plantedTree() {
            return ForestTree.plant(mock(User.class), 1, TreeKind.PINE, 7, LocalDateTime.of(2026, 11, 5, 7, 48, 10));
        }

        private ForestOnboarding completedOnboarding() {
            return ForestOnboarding.complete(mock(User.class), LocalDateTime.of(2026, 11, 10, 8, 0));
        }
    }

    private void givenArrivedDays(Long userId, int days) {
        List<LocalDateTime> arrivedAts = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            arrivedAts.add(FIRST_ARRIVED_DAY.plusDays(day).atTime(7, 48, 10));
        }
        given(alarmOccurrenceRepository.findActiveEndedAtsByUserIdAndEndType(userId, OccurrenceEndType.ARRIVED))
            .willReturn(arrivedAts);
    }
}
