package com.ringout.api.forest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ForestOnboardingTest {

    @Test
    void 온보딩_완료_시각을_초_단위로_저장한다() {
        // given
        LocalDateTime completedAt = LocalDateTime.of(2026, 11, 10, 8, 0, 0, 734_000_000);

        // when
        ForestOnboarding onboarding = ForestOnboarding.complete(mock(User.class), completedAt);

        // then
        assertThat(onboarding.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 8, 0, 0));
    }

    @Test
    void 온보딩_완료_시각이_없으면_생성할_수_없다() {
        // given
        User user = mock(User.class);

        // when // then
        assertThatThrownBy(() -> ForestOnboarding.complete(user, null))
            .isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_ONBOARDING_REQUEST_INVALID));
    }
}
