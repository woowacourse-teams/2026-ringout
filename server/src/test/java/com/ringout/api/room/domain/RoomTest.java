package com.ringout.api.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.User;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RoomTest {

    @Nested
    class 모임방_활동_요일_생성_규칙 {

        @Test
        void 생성할_때_활동_요일_목록을_관리한다() {
            // given
            List<ActivityDay> activityDays = List.of(ActivityDay.MONDAY, ActivityDay.FRIDAY);

            // when
            Room room = createRoom(activityDays);

            // then
            assertThat(room.getActivityDays())
                .containsExactly(ActivityDay.MONDAY, ActivityDay.FRIDAY);
        }
    }

    @Nested
    class 모임방_이름_검증_규칙 {

        @Test
        void 이름이_null이면_이름_형식_오류를_반환한다() {
            // given
            User hostUser = mock(User.class);
            String invalidName = null;

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                hostUser,
                null,
                invalidName,
                null,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NAME_INVALID));
        }

        @Test
        void 이름이_공백만_있으면_이름_형식_오류를_반환한다() {
            // given
            User hostUser = mock(User.class);
            String invalidName = "   ";

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                hostUser,
                null,
                invalidName,
                null,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NAME_INVALID));
        }

        @ParameterizedTest
        @MethodSource("com.ringout.api.room.domain.RoomTest#invalidNameLengths")
        void 이름_길이가_허용_범위를_벗어나면_이름_형식_오류를_반환한다(String invalidName) {
            // given
            User hostUser = mock(User.class);

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                hostUser,
                null,
                invalidName,
                null,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NAME_INVALID));
        }

        @Test
        void 이름에_허용되지_않은_문자가_포함되면_이름_형식_오류를_반환한다() {
            // given
            User hostUser = mock(User.class);
            String invalidName = "운동!";

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                hostUser,
                null,
                invalidName,
                null,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_NAME_INVALID));
        }
    }

    @Nested
    class 모임방_소개_검증_규칙 {

        @Test
        void 소개가_공백만_있으면_소개_형식_오류를_반환한다() {
            // given
            User hostUser = mock(User.class);
            String invalidDescription = "   ";

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                hostUser,
                null,
                "운동 모임",
                invalidDescription,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_DESCRIPTION_INVALID));
        }

        @Test
        void 소개가_300자를_초과하면_소개_형식_오류를_반환한다() {
            // given
            User hostUser = mock(User.class);
            String invalidDescription = "가".repeat(301);

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                hostUser,
                null,
                "운동 모임",
                invalidDescription,
                List.of(ActivityDay.MONDAY),
                LocalTime.of(8, 0)
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_DESCRIPTION_INVALID));
        }
    }

    @Nested
    class 모임방_활동_시간_검증_규칙 {

        @Test
        void 활동_시간이_없으면_시간_형식_오류를_반환한다() {
            // given
            LocalTime activityTime = null;

            // when
            Throwable thrown = catchThrowable(() -> Room.of(
                mock(User.class),
                null,
                "운동 모임",
                null,
                List.of(ActivityDay.MONDAY),
                activityTime
            ));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(RoomErrorStatus.ROOM_ACTIVITY_TIME_INVALID));
        }
    }

    @Nested
    class 모임방_활동_요일_변경_규칙 {

        @Test
        void 활동_요일을_변경한다() {
            // given
            Room room = createRoom(List.of(ActivityDay.MONDAY));
            List<ActivityDay> changedDays = List.of(ActivityDay.TUESDAY, ActivityDay.THURSDAY);

            // when
            room.changeActivityDays(changedDays);

            // then
            assertThat(room.getActivityDays())
                .containsExactly(ActivityDay.TUESDAY, ActivityDay.THURSDAY);
        }
    }

    @Nested
    class 모임방_활동_요일_개별_수정_규칙 {

        @Test
        void 활동_요일을_추가한다() {
            // given
            Room room = createRoom(List.of(ActivityDay.MONDAY));

            // when
            room.addActivityDay(ActivityDay.FRIDAY);

            // then
            assertThat(room.getActivityDays()).containsExactly(ActivityDay.MONDAY, ActivityDay.FRIDAY);
        }

        @Test
        void 활동_요일을_제거한다() {
            // given
            Room room = createRoom(List.of(ActivityDay.MONDAY, ActivityDay.FRIDAY));

            // when
            room.removeActivityDay(ActivityDay.MONDAY);

            // then
            assertThat(room.getActivityDays()).containsExactly(ActivityDay.FRIDAY);
        }
    }

    private Room createRoom(List<ActivityDay> activityDays) {
        return Room.of(
            mock(User.class),
            null,
            "운동 모임",
            "운동을 함께하는 모임입니다.",
            activityDays,
            LocalTime.of(8, 0)
        );
    }

    private static Stream<Arguments> invalidNameLengths() {
        return Stream.of(
            Arguments.of("운"),
            Arguments.of("가".repeat(21))
        );
    }

}
