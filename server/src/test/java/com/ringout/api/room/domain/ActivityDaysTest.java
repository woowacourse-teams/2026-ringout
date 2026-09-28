package com.ringout.api.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.status.RoomErrorStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActivityDaysTest {

    @Nested
    class 활동_요일_비트마스크_변환_규칙 {

        @Test
        void 비트마스크를_활동_요일_목록으로_변환한다() {
            // given
            int activityDays = 84;

            // when
            ActivityDays days = ActivityDays.fromMask(activityDays);

            // then
            assertThat(days.getDays())
                .containsExactly(ActivityDay.MONDAY, ActivityDay.WEDNESDAY, ActivityDay.FRIDAY);
        }

        @Test
        void 활동_요일_목록을_비트마스크로_변환한다() {
            // given
            ActivityDays days = ActivityDays.from(List.of(
                ActivityDay.MONDAY,
                ActivityDay.WEDNESDAY,
                ActivityDay.FRIDAY
            ));

            // when
            int mask = days.toMask();

            // then
            assertThat(mask).isEqualTo(84);
        }
    }

    @Nested
    class 활동_요일_개별_수정_규칙 {

        @Test
        void 활동_요일을_추가한다() {
            // given
            ActivityDays original = ActivityDays.from(List.of(ActivityDay.MONDAY));

            // when
            ActivityDays added = original.add(ActivityDay.FRIDAY);

            // then
            assertThat(original.getDays()).containsExactly(ActivityDay.MONDAY);
            assertThat(added.getDays()).containsExactly(ActivityDay.MONDAY, ActivityDay.FRIDAY);
        }

        @Test
        void 활동_요일을_제거한다() {
            // given
            ActivityDays original = ActivityDays.from(List.of(ActivityDay.MONDAY, ActivityDay.FRIDAY));

            // when
            ActivityDays removed = original.remove(ActivityDay.MONDAY);

            // then
            assertThat(original.getDays()).containsExactly(ActivityDay.MONDAY, ActivityDay.FRIDAY);
            assertThat(removed.getDays()).containsExactly(ActivityDay.FRIDAY);
        }
    }

    @Nested
    class 활동_요일_목록_유효성_규칙 {

        @Test
        void 활동_요일이_없으면_필수_요일_오류를_반환한다() {
            // given
            List<ActivityDay> activityDays = List.of();

            // when
            Throwable thrown = catchThrowable(() -> ActivityDays.from(activityDays));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_DAYS_REQUIRED));
        }

        @Test
        void 활동_요일에_null이_포함되면_요일_형식_오류를_반환한다() {
            // given
            List<ActivityDay> activityDays = Arrays.asList(ActivityDay.MONDAY, null);

            // when
            Throwable thrown = catchThrowable(() -> ActivityDays.from(activityDays));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_DAY_INVALID));
        }

        @Test
        void 활동_요일이_중복되면_요일_형식_오류를_반환한다() {
            // given
            List<ActivityDay> duplicatedDays = List.of(ActivityDay.MONDAY, ActivityDay.MONDAY);

            // when
            Throwable thrown = catchThrowable(() -> ActivityDays.from(duplicatedDays));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_DAY_INVALID));
        }
    }
}
