package com.ringout.api.forest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.domain.ForestOnboarding;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingCompleteResult;
import com.ringout.api.forest.repository.ForestOnboardingRepository;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForestServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),
        ZoneId.of("Asia/Seoul"));

    @Mock
    private ForestOnboardingRepository forestOnboardingRepository;

    @Mock
    private UserRepository userRepository;

    private ForestService forestService;

    @BeforeEach
    void setUp() {
        forestService = new ForestService(forestOnboardingRepository, userRepository, FIXED_CLOCK);
    }

    @Nested
    class 숲_온보딩_완료 {

        @Test
        void 완료_시각이_없으면_요청_시각을_저장하고_생성으로_응답한다() {
            // given
            Long userId = 1L;
            User user = mock(User.class);
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.empty());
            given(forestOnboardingRepository.save(any(ForestOnboarding.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            ForestOnboardingCompleteResult result = forestService.completeOnboarding(userId,
                new ForestOnboardingCompleteRequest(OffsetDateTime.parse("2026-11-10T08:00:00+09:00")));

            // then
            assertThat(result.created()).isTrue();
            assertThat(result.response().onboardingCompletedAt())
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+09:00"));
            verify(forestOnboardingRepository).save(any(ForestOnboarding.class));
        }

        @Test
        void 다른_시간대의_요청_시각을_서버_시간대로_변환해_저장한다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.empty());
            given(forestOnboardingRepository.save(any(ForestOnboarding.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            ForestOnboardingCompleteResult result = forestService.completeOnboarding(userId,
                new ForestOnboardingCompleteRequest(OffsetDateTime.parse("2026-11-09T23:00:00Z")));

            // then
            assertThat(result.response().onboardingCompletedAt())
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+09:00"));
        }

        @Test
        void 이미_완료_시각이_있으면_변경하지_않고_저장된_시각을_반환한다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(mock(User.class)));
            ForestOnboarding saved = ForestOnboarding.complete(mock(User.class),
                LocalDateTime.of(2026, 11, 10, 8, 0, 0));
            given(forestOnboardingRepository.findByUserId(userId)).willReturn(Optional.of(saved));

            // when
            ForestOnboardingCompleteResult result = forestService.completeOnboarding(userId,
                new ForestOnboardingCompleteRequest(OffsetDateTime.parse("2026-11-12T09:30:00+09:00")));

            // then
            assertThat(result.created()).isFalse();
            assertThat(result.response().onboardingCompletedAt())
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+09:00"));
            verify(forestOnboardingRepository, never()).save(any());
        }

        @Test
        void 요청_본문이_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestService.completeOnboarding(1L, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_ONBOARDING_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestOnboardingRepository);
        }

        @Test
        void 완료_시각이_없으면_FOREST400을_던진다() {
            // when // then
            assertThatThrownBy(() -> forestService.completeOnboarding(1L, new ForestOnboardingCompleteRequest(null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_ONBOARDING_REQUEST_INVALID));
            verifyNoInteractions(userRepository, forestOnboardingRepository);
        }

        @Test
        void 인증된_사용자가_존재하지_않으면_FOREST401을_던진다() {
            // given
            Long userId = 1L;
            given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.empty());

            // when // then
            assertThatThrownBy(() -> forestService.completeOnboarding(userId,
                new ForestOnboardingCompleteRequest(OffsetDateTime.parse("2026-11-10T08:00:00+09:00"))))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_UNAUTHORIZED));
            verifyNoInteractions(forestOnboardingRepository);
        }
    }
}
