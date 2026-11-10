package com.ringout.api.alarm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmDay;
import com.ringout.api.alarm.domain.AlarmSetting;
import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.dto.response.AlarmUpsertResponse;
import com.ringout.api.alarm.repository.AlarmRepository;
import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    private static final Long USER_ID = 1L;
    private static final String ALARM_ID = "alarm-001";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private AlarmRepository alarmRepository;

    @Mock
    private UserRepository userRepository;

    private AlarmService alarmService;

    @BeforeEach
    void setUp() {
        alarmService = new AlarmService(alarmRepository, userRepository, CLOCK);
        lenient().when(userRepository.existsById(any())).thenReturn(true);
    }

    private AlarmUpsertRequest request(Long alarmVersion) {
        return new AlarmUpsertRequest(LocalTime.of(8, 0), List.of(AlarmDay.FRIDAY, AlarmDay.MONDAY), 10,
            "우아한테크코스", 37.5502, 127.0738, alarmVersion);
    }

    static Stream<Arguments> invalidAlarmRequests() {
        return Stream.of(
            Arguments.of(null, List.of(AlarmDay.MONDAY), 5, "세종대학교", 37.5502, 127.0738),
            Arguments.of(LocalTime.of(7, 0), null, 5, "세종대학교", 37.5502, 127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY, AlarmDay.MONDAY), 5, "세종대학교", 37.5502,
                127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), null, "세종대학교", 37.5502, 127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 31, "세종대학교", 37.5502, 127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, null, 37.5502, 127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "123456789012345678901", 37.5502,
                127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "세종대학교", null, 127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "세종대학교", 90.1, 127.0738),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "세종대학교", 37.5502, null),
            Arguments.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "세종대학교", 37.5502, -180.1)
        );
    }

    @Nested
    class 알람_upsert {

        @Test
        void 검증된_값으로_upsert하고_서버에_최종_저장된_알람을_반환한다() {
            // given
            AlarmSetting storedSetting = AlarmSetting.of(LocalTime.of(9, 0), List.of(AlarmDay.TUESDAY), 5,
                "세종대학교", 37.55, 127.07);
            Alarm stored = Alarm.create(mock(User.class), ALARM_ID, storedSetting, 3L);
            given(alarmRepository.findOwnedAlarmForUpdate(USER_ID, ALARM_ID)).willReturn(Optional.of(stored));

            // when
            AlarmUpsertResponse response = alarmService.upsertAlarm(USER_ID, ALARM_ID,
                new AlarmUpsertRequest(LocalTime.of(8, 0), List.of(AlarmDay.FRIDAY, AlarmDay.MONDAY), 10,
                    "  우아한테크코스  ", 37.5502, 127.0738, 2L));

            // then
            verify(alarmRepository).upsertOwnedAlarm(USER_ID, ALARM_ID, LocalTime.of(8, 0), 64 + 4, 10, "우아한테크코스",
                37.5502, 127.0738, 2L, LocalDateTime.of(2026, 11, 10, 12, 0));
            assertThat(response).isEqualTo(new AlarmUpsertResponse(ALARM_ID, LocalTime.of(9, 0),
                List.of(AlarmDay.TUESDAY), 5, "세종대학교", 37.55, 127.07, 3L));
        }

        @Test
        void 빈_요일_목록은_반복_없는_알람으로_upsert한다() {
            // given
            AlarmSetting storedSetting = AlarmSetting.of(LocalTime.of(8, 0), List.of(), 0, "세종대학교", 37.55, 127.07);
            Alarm stored = Alarm.create(mock(User.class), ALARM_ID, storedSetting, 1L);
            given(alarmRepository.findOwnedAlarmForUpdate(USER_ID, ALARM_ID)).willReturn(Optional.of(stored));

            // when
            AlarmUpsertResponse response = alarmService.upsertAlarm(USER_ID, ALARM_ID,
                new AlarmUpsertRequest(LocalTime.of(8, 0), List.of(), 0, "세종대학교", 37.55, 127.07, 1L));

            // then
            verify(alarmRepository).upsertOwnedAlarm(USER_ID, ALARM_ID, LocalTime.of(8, 0), 0, 0, "세종대학교", 37.55,
                127.07, 1L, LocalDateTime.of(2026, 11, 10, 12, 0));
            assertThat(response.dayOfWeek()).isEmpty();
        }

        @Test
        void 사용자_id가_없으면_upsert할_수_없다() {
            // given

            // when // then
            assertThatThrownBy(() -> alarmService.upsertAlarm(null, ALARM_ID, request(1L)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_UNAUTHORIZED));
            verifyNoInteractions(alarmRepository);
        }

        @Test
        void 사용자가_존재하지_않으면_upsert할_수_없다() {
            // given
            given(userRepository.existsById(USER_ID)).willReturn(false);

            // when // then
            assertThatThrownBy(() -> alarmService.upsertAlarm(USER_ID, ALARM_ID, request(1L)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_UNAUTHORIZED));
            verifyNoInteractions(alarmRepository);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "alarm 1", "1234567890123456789012345678901234567"})
        void 알람_ID가_올바르지_않으면_upsert할_수_없다(String alarmId) {
            // given

            // when // then
            assertThatThrownBy(() -> alarmService.upsertAlarm(USER_ID, alarmId, request(1L)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_ID_INVALID));
            verifyNoInteractions(alarmRepository);
        }

        @Test
        void 요청_body가_없으면_upsert할_수_없다() {
            // given

            // when // then
            assertThatThrownBy(() -> alarmService.upsertAlarm(USER_ID, ALARM_ID, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
            verifyNoInteractions(alarmRepository);
        }

        @ParameterizedTest
        @MethodSource("com.ringout.api.alarm.service.AlarmServiceTest#invalidAlarmRequests")
        void 알람_요청_정보가_올바르지_않으면_upsert할_수_없다(LocalTime time, List<AlarmDay> days, Integer repeatTime,
            String destinationName, Double latitude, Double longitude) {
            // given
            AlarmUpsertRequest request = new AlarmUpsertRequest(time, days, repeatTime, destinationName, latitude,
                longitude, 1L);

            // when // then
            assertThatThrownBy(() -> alarmService.upsertAlarm(USER_ID, ALARM_ID, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
            verifyNoInteractions(alarmRepository);
        }

        @Test
        void 알람_버전이_없으면_upsert할_수_없다() {
            // given

            // when // then
            assertThatThrownBy(() -> alarmService.upsertAlarm(USER_ID, ALARM_ID, request(null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_VERSION_INVALID));
            verifyNoInteractions(alarmRepository);
        }
    }
}
