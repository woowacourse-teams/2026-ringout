package com.ringout.api.alarmoccurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.domain.RingingType;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceEventRequest;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceStartRequest;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceDayResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceDetailResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceStartResult;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrencesResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmRingingResponse;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.alarmoccurrence.repository.AlarmRingingRepository;
import com.ringout.api.alarmoccurrence.status.AlarmOccurrenceErrorStatus;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.service.RoomActivityService;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlarmOccurrenceServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-22T23:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final String OCCURRENCE_ID = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f";

    @Mock
    private AlarmOccurrenceRepository alarmOccurrenceRepository;

    @Mock
    private AlarmRingingRepository alarmRingingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomActivityService roomActivityService;

    private AlarmOccurrenceService alarmOccurrenceService;

    @BeforeEach
    void setUp() {
        alarmOccurrenceService = new AlarmOccurrenceService(
            alarmOccurrenceRepository, alarmRingingRepository, userRepository, roomActivityService, CLOCK);
    }

    @Nested
    class 개인_알람_사용_기록_조회 {

        private AlarmOccurrenceDayResponse dayOf(AlarmOccurrencesResponse response, int dayOfMonth) {
            return response.days().get(dayOfMonth - 1);
        }

        @Test
        void 기록이_없는_달도_모든_날짜를_빈_실행_목록으로_반환한다() {
            // given
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, START, END))
                .willReturn(List.of());

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, MONTH);

            // then
            assertThat(response.month()).isEqualTo(MONTH);
            assertThat(response.days())
                .extracting(AlarmOccurrenceDayResponse::date)
                .containsExactlyElementsOf(LocalDate.of(2026, 8, 1).datesUntil(LocalDate.of(2026, 9, 1)).toList());
            assertThat(response.days()).allSatisfy(day -> assertThat(day.occurrences()).isEmpty());
            verifyNoInteractions(alarmRingingRepository);
        }

        @ParameterizedTest
        @CsvSource({"2026-02, 28", "2028-02, 29", "2026-04, 30", "2026-12, 31"})
        void 조회_월의_일수만큼_날짜를_반환한다(String month, int lengthOfMonth) {
            // given
            YearMonth yearMonth = YearMonth.parse(month);
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(any(), any(), any()))
                .willReturn(List.of());

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, yearMonth);

            // then
            assertThat(response.days()).hasSize(lengthOfMonth);
            assertThat(response.days().get(0).date()).isEqualTo(yearMonth.atDay(1));
            assertThat(response.days().get(lengthOfMonth - 1).date()).isEqualTo(yearMonth.atEndOfMonth());
        }

        @Test
        void 조회_월의_첫날_시작부터_다음_달_첫날_시작_전까지를_최초_울림_시각_범위로_조회한다() {
            // given
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, START, END))
                .willReturn(List.of());

            // when
            alarmOccurrenceService.getAlarmOccurrences(USER_ID, MONTH);

            // then
            verify(alarmOccurrenceRepository).findActiveByUserIdAndStartedAtBetween(
                USER_ID, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        }

        @Test
        void 십이월은_다음_해_일월_첫날_시작_전까지를_조회한다() {
            // given
            YearMonth december = YearMonth.of(2026, 12);
            LocalDateTime start = LocalDateTime.of(2026, 12, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2027, 1, 1, 0, 0);
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, start, end))
                .willReturn(List.of());

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, december);

            // then
            assertThat(response.month()).isEqualTo(december);
            verify(alarmOccurrenceRepository).findActiveByUserIdAndStartedAtBetween(USER_ID, start, end);
        }

        @Test
        void 실행을_최초_울림_날짜별로_묶어_최초_울림_시각_오름차순으로_반환한다() {
            // given
            AlarmOccurrence first = occurrenceWithId(10L, "alarm-morning", LocalDateTime.of(2026, 8, 1, 6, 30));
            first.dismiss(null, LocalDateTime.of(2026, 8, 1, 6, 31, 2));
            first.ringRepeat("E1", LocalDateTime.of(2026, 8, 1, 6, 35));
            first.dismiss("E1", LocalDateTime.of(2026, 8, 1, 6, 35, 40));
            first.end(OccurrenceEndType.ARRIVED, LocalDateTime.of(2026, 8, 1, 6, 53, 10));
            AlarmOccurrence second = occurrenceWithId(11L, "alarm-morning", LocalDateTime.of(2026, 8, 17, 6, 30));
            AlarmOccurrence third = occurrenceWithId(12L, "alarm-evening", LocalDateTime.of(2026, 8, 17, 7, 0));
            AlarmOccurrence fourth = occurrenceWithId(13L, "alarm-evening", LocalDateTime.of(2026, 8, 31, 18, 0));
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, START, END))
                .willReturn(List.of(first, second, third, fourth));
            given(alarmRingingRepository.findActiveByAlarmOccurrenceIds(List.of(10L, 11L, 12L, 13L)))
                .willReturn(ringingsOf(first, second, third, fourth));

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, MONTH);

            // then
            assertThat(response.days()).hasSize(31);
            assertThat(dayOf(response, 1).occurrences())
                .extracting(AlarmOccurrenceResponse::alarmOccurrenceId)
                .containsExactly(first.getOccurrenceUuid());
            assertThat(dayOf(response, 17).occurrences())
                .extracting(AlarmOccurrenceResponse::alarmOccurrenceId)
                .containsExactly(second.getOccurrenceUuid(), third.getOccurrenceUuid());
            assertThat(dayOf(response, 31).occurrences())
                .extracting(AlarmOccurrenceResponse::alarmOccurrenceId)
                .containsExactly(fourth.getOccurrenceUuid());
            assertThat(dayOf(response, 2).occurrences()).isEmpty();

            AlarmOccurrenceResponse firstResponse = dayOf(response, 1).occurrences().get(0);
            assertThat(firstResponse.alarmId()).isEqualTo("alarm-morning");
            assertThat(firstResponse.alarmTime()).isEqualTo(LocalTime.of(6, 30));
            assertThat(firstResponse.endType()).isEqualTo(OccurrenceEndType.ARRIVED);
            assertThat(firstResponse.endedAt()).isEqualTo(OffsetDateTime.parse("2026-08-01T06:53:10+09:00"));
            assertThat(firstResponse.ringings()).containsExactly(
                new AlarmRingingResponse(RingingType.INITIAL, null,
                    OffsetDateTime.parse("2026-08-01T06:30:00+09:00"),
                    OffsetDateTime.parse("2026-08-01T06:31:02+09:00")),
                new AlarmRingingResponse(RingingType.REPEAT, "E1",
                    OffsetDateTime.parse("2026-08-01T06:35:00+09:00"),
                    OffsetDateTime.parse("2026-08-01T06:35:40+09:00"))
            );
        }

        @Test
        void 최초_울림_다음_날로_넘어간_재울림과_종료도_최초_울림_날짜에_포함한다() {
            // given
            AlarmOccurrence occurrence = occurrenceWithId(10L, "alarm-night", LocalDateTime.of(2026, 8, 17, 23, 55));
            occurrence.dismiss(null, LocalDateTime.of(2026, 8, 17, 23, 56));
            occurrence.ringRepeat("E1", LocalDateTime.of(2026, 8, 18, 0, 5));
            occurrence.end(OccurrenceEndType.ARRIVED, LocalDateTime.of(2026, 8, 18, 0, 10));
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, START, END))
                .willReturn(List.of(occurrence));
            given(alarmRingingRepository.findActiveByAlarmOccurrenceIds(List.of(10L)))
                .willReturn(ringingsOf(occurrence));

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, MONTH);

            // then
            assertThat(dayOf(response, 17).occurrences()).hasSize(1);
            assertThat(dayOf(response, 17).occurrences().get(0).ringings())
                .extracting(AlarmRingingResponse::ringingAt)
                .containsExactly(
                    OffsetDateTime.parse("2026-08-17T23:55:00+09:00"),
                    OffsetDateTime.parse("2026-08-18T00:05:00+09:00"));
            assertThat(dayOf(response, 18).occurrences()).isEmpty();
        }

        @Test
        void 최초_울림이_조회_월_마지막_날이면_다음_달로_넘어간_재울림과_종료도_마지막_날에_포함한다() {
            // given
            AlarmOccurrence occurrence = occurrenceWithId(10L, "alarm-night", LocalDateTime.of(2026, 8, 31, 23, 55));
            occurrence.dismiss(null, LocalDateTime.of(2026, 8, 31, 23, 56));
            occurrence.ringRepeat("E1", LocalDateTime.of(2026, 9, 1, 0, 5));
            occurrence.end(OccurrenceEndType.FORCE_ENDED, LocalDateTime.of(2026, 9, 1, 0, 10));
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, START, END))
                .willReturn(List.of(occurrence));
            given(alarmRingingRepository.findActiveByAlarmOccurrenceIds(List.of(10L)))
                .willReturn(ringingsOf(occurrence));

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, MONTH);

            // then
            AlarmOccurrenceResponse occurrenceResponse = dayOf(response, 31).occurrences().get(0);
            assertThat(occurrenceResponse.ringings())
                .extracting(AlarmRingingResponse::ringingAt)
                .containsExactly(
                    OffsetDateTime.parse("2026-08-31T23:55:00+09:00"),
                    OffsetDateTime.parse("2026-09-01T00:05:00+09:00"));
            assertThat(occurrenceResponse.endType()).isEqualTo(OccurrenceEndType.FORCE_ENDED);
            assertThat(occurrenceResponse.endedAt()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:10:00+09:00"));
        }

        @Test
        void 끄지_않은_울림과_종료되지_않은_실행은_null로_반환한다() {
            // given
            AlarmOccurrence occurrence = occurrenceWithId(10L, "alarm-morning", LocalDateTime.of(2026, 8, 17, 6, 30));
            given(alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(USER_ID, START, END))
                .willReturn(List.of(occurrence));
            given(alarmRingingRepository.findActiveByAlarmOccurrenceIds(List.of(10L)))
                .willReturn(ringingsOf(occurrence));

            // when
            AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(USER_ID, MONTH);

            // then
            AlarmOccurrenceResponse occurrenceResponse = dayOf(response, 17).occurrences().get(0);
            assertThat(occurrenceResponse.endType()).isNull();
            assertThat(occurrenceResponse.endedAt()).isNull();
            assertThat(occurrenceResponse.ringings().get(0).dismissedAt()).isNull();
        }
    }

    @Nested
    class 알람_실행_시작 {

        private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-09-23T07:00:00+09:00");
        private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-09-23T07:00:02+09:00");

        private final AlarmOccurrenceStartRequest request = new AlarmOccurrenceStartRequest(
            "alarm-1", SCHEDULED_AT, STARTED_AT);

        @Test
        void 새_실행이면_기기가_보낸_시각을_최초_울림으로_기록하고_생성한다() {
            // given
            given(alarmOccurrenceRepository.findActiveByUserIdAndClientAlarmIdAndScheduledAt(
                USER_ID, "alarm-1", LocalDateTime.of(2026, 9, 23, 7, 0))).willReturn(Optional.empty());
            given(userRepository.getReferenceById(USER_ID)).willReturn(userWithId(USER_ID));
            given(alarmOccurrenceRepository.save(any(AlarmOccurrence.class))).willAnswer(returnsFirstArg());

            // when
            AlarmOccurrenceStartResult result = alarmOccurrenceService.startAlarmOccurrence(USER_ID, request);

            // then
            assertThat(result.created()).isTrue();
            AlarmOccurrenceDetailResponse response = result.response();
            assertThat(response.alarmOccurrenceId()).hasSize(36);
            assertThat(response.startedAt()).isEqualTo(OffsetDateTime.parse("2026-09-23T07:00:02+09:00"));
            assertThat(response.ringings()).containsExactly(new AlarmRingingResponse(
                RingingType.INITIAL, null, OffsetDateTime.parse("2026-09-23T07:00:02+09:00"), null));
            assertThat(response.arrivedAt()).isNull();
            assertThat(response.forceEndedAt()).isNull();

            ArgumentCaptor<AlarmOccurrence> captor = ArgumentCaptor.forClass(AlarmOccurrence.class);
            verify(alarmOccurrenceRepository).save(captor.capture());
            assertThat(captor.getValue().getClientAlarmId()).isEqualTo("alarm-1");
            assertThat(captor.getValue().getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 23, 7, 0));
            assertThat(captor.getValue().getAlarmTime()).isEqualTo(LocalTime.of(7, 0));
            verify(roomActivityService).recordMemberAlarmActivity(USER_ID, NOW);
        }

        @Test
        void 같은_알람과_예정_일시의_실행이_있으면_새로_만들지_않고_기존_실행을_반환한다() {
            // given
            AlarmOccurrence existing = occurrenceWithId(10L, "alarm-1", LocalDateTime.of(2026, 9, 23, 6, 59, 32));
            given(alarmOccurrenceRepository.findActiveByUserIdAndClientAlarmIdAndScheduledAt(
                USER_ID, "alarm-1", LocalDateTime.of(2026, 9, 23, 7, 0))).willReturn(Optional.of(existing));

            // when
            AlarmOccurrenceStartResult result = alarmOccurrenceService.startAlarmOccurrence(USER_ID, request);

            // then
            assertThat(result.created()).isFalse();
            assertThat(result.response().alarmOccurrenceId()).isEqualTo(existing.getOccurrenceUuid());
            assertThat(result.response().startedAt()).isEqualTo(OffsetDateTime.parse("2026-09-23T06:59:32+09:00"));
            verify(alarmOccurrenceRepository, never()).save(any());
        }

        @Test
        void 예정_일시는_서버_시간대로_환산해_중복을_확인한다() {
            // given
            AlarmOccurrenceStartRequest utcRequest = new AlarmOccurrenceStartRequest(
                "alarm-1", OffsetDateTime.parse("2026-09-22T22:00:00Z"), STARTED_AT);
            AlarmOccurrence existing = occurrenceWithId(10L, "alarm-1", NOW);
            given(alarmOccurrenceRepository.findActiveByUserIdAndClientAlarmIdAndScheduledAt(
                USER_ID, "alarm-1", LocalDateTime.of(2026, 9, 23, 7, 0))).willReturn(Optional.of(existing));

            // when
            AlarmOccurrenceStartResult result = alarmOccurrenceService.startAlarmOccurrence(USER_ID, utcRequest);

            // then
            assertThat(result.created()).isFalse();
        }

        @Test
        void 알람_설정_시각은_예정_일시를_서버_시간대로_환산한_시각이다() {
            // given
            AlarmOccurrenceStartRequest utcRequest = new AlarmOccurrenceStartRequest(
                "alarm-1", OffsetDateTime.parse("2026-09-22T22:00:00Z"), STARTED_AT);
            given(alarmOccurrenceRepository.findActiveByUserIdAndClientAlarmIdAndScheduledAt(
                USER_ID, "alarm-1", LocalDateTime.of(2026, 9, 23, 7, 0))).willReturn(Optional.empty());
            given(userRepository.getReferenceById(USER_ID)).willReturn(userWithId(USER_ID));
            given(alarmOccurrenceRepository.save(any(AlarmOccurrence.class))).willAnswer(returnsFirstArg());

            // when
            alarmOccurrenceService.startAlarmOccurrence(USER_ID, utcRequest);

            // then
            ArgumentCaptor<AlarmOccurrence> captor = ArgumentCaptor.forClass(AlarmOccurrence.class);
            verify(alarmOccurrenceRepository).save(captor.capture());
            assertThat(captor.getValue().getAlarmTime()).isEqualTo(LocalTime.of(7, 0));
        }

        @Test
        void 알람_ID가_없거나_공백이면_시작할_수_없다() {
            // when
            Throwable nullId = catchThrowable(() -> alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest(null, SCHEDULED_AT, STARTED_AT)));
            Throwable blankId = catchThrowable(() -> alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest(" ", SCHEDULED_AT, STARTED_AT)));

            // then
            assertError(nullId, AlarmOccurrenceErrorStatus.ALARM_ID_REQUIRED);
            assertError(blankId, AlarmOccurrenceErrorStatus.ALARM_ID_REQUIRED);
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 알람_ID가_64자를_넘으면_시작할_수_없다() {
            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest("a".repeat(65), SCHEDULED_AT, STARTED_AT)));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.ALARM_ID_TOO_LONG);
        }

        @Test
        void 예정_일시가_없으면_시작할_수_없다() {
            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest("alarm-1", null, STARTED_AT)));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.SCHEDULED_AT_REQUIRED);
        }

        @Test
        void 최초_울림_시각이_없으면_시작할_수_없다() {
            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest("alarm-1", SCHEDULED_AT, null)));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.STARTED_AT_REQUIRED);
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 최초_울림_시각이_서버_시각보다_1분_넘게_미래면_시작할_수_없다() {
            // given
            OffsetDateTime tooLate = OffsetDateTime.parse("2026-09-23T08:01:01+09:00");

            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest("alarm-1", SCHEDULED_AT, tooLate)));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.EVENT_TIME_IN_FUTURE);
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 서버_시각보다_1분_이내로_앞선_기기_시각은_허용한다() {
            // given
            OffsetDateTime slightlyAhead = OffsetDateTime.parse("2026-09-23T08:01:00+09:00");
            given(alarmOccurrenceRepository.findActiveByUserIdAndClientAlarmIdAndScheduledAt(
                USER_ID, "alarm-1", LocalDateTime.of(2026, 9, 23, 7, 0))).willReturn(Optional.empty());
            given(userRepository.getReferenceById(USER_ID)).willReturn(userWithId(USER_ID));
            given(alarmOccurrenceRepository.save(any(AlarmOccurrence.class))).willAnswer(returnsFirstArg());

            // when
            AlarmOccurrenceStartResult result = alarmOccurrenceService.startAlarmOccurrence(USER_ID,
                new AlarmOccurrenceStartRequest("alarm-1", SCHEDULED_AT, slightlyAhead));

            // then
            assertThat(result.response().startedAt()).isEqualTo(slightlyAhead);
        }
    }

    @Nested
    class 알람_실행_이벤트_저장 {

        @Test
        void 재울림과_끈_시각을_기기가_보낸_시각으로_함께_저장한다() {
            // given
            AlarmOccurrence occurrence = givenOwnedOccurrence();
            AlarmOccurrenceEventRequest request = new AlarmOccurrenceEventRequest(
                "E1", OffsetDateTime.parse("2026-09-23T07:05:00+09:00"),
                OffsetDateTime.parse("2026-09-23T07:05:40+09:00"), null, null);

            // when
            AlarmOccurrenceDetailResponse response = alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request);

            // then
            assertThat(response.ringings()).hasSize(2);
            assertThat(response.ringings().get(1)).isEqualTo(new AlarmRingingResponse(RingingType.REPEAT, "E1",
                OffsetDateTime.parse("2026-09-23T07:05:00+09:00"),
                OffsetDateTime.parse("2026-09-23T07:05:40+09:00")));
            assertThat(response.ringings().get(0).dismissedAt()).isNull();
            assertThat(occurrence.getRingings()).hasSize(2);
            verify(roomActivityService).recordMemberAlarmActivity(USER_ID, NOW);
        }

        @Test
        void eventId_없이_끈_시각이_오면_최초_울림을_끈다() {
            // given
            givenOwnedOccurrence();
            AlarmOccurrenceEventRequest request = new AlarmOccurrenceEventRequest(
                null, null, OffsetDateTime.parse("2026-09-23T07:01:10+09:00"), null, null);

            // when
            AlarmOccurrenceDetailResponse response = alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request);

            // then
            assertThat(response.ringings()).singleElement()
                .extracting(AlarmRingingResponse::dismissedAt)
                .isEqualTo(OffsetDateTime.parse("2026-09-23T07:01:10+09:00"));
        }

        @Test
        void 도착하면_도착_시각만_채워서_반환한다() {
            // given
            givenOwnedOccurrence();
            AlarmOccurrenceEventRequest request = new AlarmOccurrenceEventRequest(
                null, null, null, OffsetDateTime.parse("2026-09-23T07:48:10+09:00"), null);

            // when
            AlarmOccurrenceDetailResponse response = alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request);

            // then
            assertThat(response.arrivedAt()).isEqualTo(OffsetDateTime.parse("2026-09-23T07:48:10+09:00"));
            assertThat(response.forceEndedAt()).isNull();
        }

        @Test
        void 강제_종료하면_강제_종료_시각만_채워서_반환한다() {
            // given
            givenOwnedOccurrence();
            AlarmOccurrenceEventRequest request = new AlarmOccurrenceEventRequest(
                null, null, null, null, OffsetDateTime.parse("2026-09-23T07:30:00+09:00"));

            // when
            AlarmOccurrenceDetailResponse response = alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request);

            // then
            assertThat(response.arrivedAt()).isNull();
            assertThat(response.forceEndedAt()).isEqualTo(OffsetDateTime.parse("2026-09-23T07:30:00+09:00"));
        }

        @Test
        void 종료된_실행에_새_재울림을_보내면_409를_반환한다() {
            // given
            AlarmOccurrence occurrence = givenOwnedOccurrence();
            occurrence.end(OccurrenceEndType.ARRIVED, LocalDateTime.of(2026, 9, 23, 7, 40));
            AlarmOccurrenceEventRequest request = new AlarmOccurrenceEventRequest(
                "E9", OffsetDateTime.parse("2026-09-23T07:41:00+09:00"), null, null, null);

            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ALREADY_ENDED);
        }

        @Test
        void 실행_기록이_없으면_404를_반환한다() {
            // given
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(OCCURRENCE_ID))
                .willReturn(Optional.empty());
            AlarmOccurrenceEventRequest request = repeatRequest("E1");

            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_NOT_FOUND);
        }

        @Test
        void 다른_사용자의_실행_기록이면_403을_반환한다() {
            // given
            AlarmOccurrence occurrence = AlarmOccurrence.start(userWithId(OTHER_USER_ID), "alarm-1", NOW, LocalTime.of(7, 0), NOW);
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(OCCURRENCE_ID))
                .willReturn(Optional.of(occurrence));
            AlarmOccurrenceEventRequest request = repeatRequest("E1");

            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_FORBIDDEN);
            assertThat(occurrence.getRingings()).hasSize(1);
        }

        @Test
        void 실행_ID가_UUID_형식이_아니면_400을_반환한다() {
            // given
            AlarmOccurrenceEventRequest request = repeatRequest("E1");

            // when
            Throwable notUuid = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, "alarm-1:not-uuid", request));
            Throwable nonCanonical = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, "1-1-1-1-1", request));

            // then
            assertError(notUuid, AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ID_INVALID);
            assertError(nonCanonical, AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ID_INVALID);
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 이벤트가_하나도_없으면_400을_반환한다() {
            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, new AlarmOccurrenceEventRequest(null, null, null, null, null)));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_EVENT_EMPTY);
        }

        @Test
        void 도착과_강제_종료를_동시에_보내면_400을_반환한다() {
            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, new AlarmOccurrenceEventRequest(null, null, null,
                    OffsetDateTime.parse("2026-09-23T07:48:10+09:00"),
                    OffsetDateTime.parse("2026-09-23T07:48:10+09:00"))));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.END_TIMES_CONFLICT);
        }

        @Test
        void 재울림_ID가_공백이거나_64자를_넘으면_400을_반환한다() {
            // when
            Throwable blank = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, repeatRequest(" ")));
            Throwable tooLong = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, repeatRequest("e".repeat(65))));

            // then
            assertError(blank, AlarmOccurrenceErrorStatus.EVENT_ID_BLANK);
            assertError(tooLong, AlarmOccurrenceErrorStatus.EVENT_ID_TOO_LONG);
        }

        @Test
        void 재울림_ID와_재울림_시각은_함께_보내야_한다() {
            // when
            Throwable withoutRingingAt = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, new AlarmOccurrenceEventRequest("E1", null, null, null, null)));
            Throwable withoutEventId = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, new AlarmOccurrenceEventRequest(
                    null, OffsetDateTime.parse("2026-09-23T07:05:00+09:00"), null, null, null)));

            // then
            assertError(withoutRingingAt, AlarmOccurrenceErrorStatus.RINGING_AT_REQUIRED);
            assertError(withoutEventId, AlarmOccurrenceErrorStatus.EVENT_ID_REQUIRED);
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 이벤트_시각이_서버_시각보다_1분_넘게_미래면_400을_반환한다() {
            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, new AlarmOccurrenceEventRequest(null, null, null,
                    OffsetDateTime.parse("2026-09-23T08:01:01+09:00"), null)));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.EVENT_TIME_IN_FUTURE);
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 끈_시각이_재울림_시각보다_빠르면_400을_반환한다() {
            // given
            AlarmOccurrence occurrence = givenOwnedOccurrence();
            AlarmOccurrenceEventRequest request = new AlarmOccurrenceEventRequest(
                "E1", OffsetDateTime.parse("2026-09-23T07:05:00+09:00"),
                OffsetDateTime.parse("2026-09-23T07:04:59+09:00"), null, null);

            // when
            Throwable thrown = catchThrowable(() -> alarmOccurrenceService.recordAlarmOccurrenceEvent(
                USER_ID, OCCURRENCE_ID, request));

            // then
            assertError(thrown, AlarmOccurrenceErrorStatus.EVENT_TIME_ORDER_INVALID);
        }

        private AlarmOccurrenceEventRequest repeatRequest(String eventId) {
            return new AlarmOccurrenceEventRequest(
                eventId, OffsetDateTime.parse("2026-09-23T07:05:00+09:00"), null, null, null);
        }

        private AlarmOccurrence givenOwnedOccurrence() {
            AlarmOccurrence occurrence = AlarmOccurrence.start(userWithId(USER_ID), "alarm-1",
                LocalDateTime.of(2026, 9, 23, 7, 0), LocalTime.of(7, 0), LocalDateTime.of(2026, 9, 23, 7, 0, 2));
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(OCCURRENCE_ID))
                .willReturn(Optional.of(occurrence));
            return occurrence;
        }
    }

    private AlarmOccurrence occurrenceWithId(Long id, String clientAlarmId, LocalDateTime startedAt) {
        AlarmOccurrence occurrence = AlarmOccurrence.start(null, clientAlarmId, startedAt.withSecond(0),
            startedAt.toLocalTime().withSecond(0), startedAt);
        ReflectionTestUtils.setField(occurrence, "id", id);
        return occurrence;
    }

    private List<AlarmRinging> ringingsOf(AlarmOccurrence... occurrences) {
        return Stream.of(occurrences)
            .flatMap(occurrence -> occurrence.getRingings().stream())
            .toList();
    }

    private User userWithId(Long userId) {
        User user = User.register(SocialProvider.KAKAO, "user-" + userId, null, NOW.minusDays(1));
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private void assertError(Throwable thrown, AlarmOccurrenceErrorStatus expected) {
        assertThat(thrown).isInstanceOf(GeneralException.class);
        assertThat(((GeneralException) thrown).getCode()).isEqualTo(expected);
    }
}
