package com.ringout.api.alarmoccurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ringout.api.alarmoccurrence.status.AlarmOccurrenceErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlarmOccurrenceTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 9, 23, 7, 0, 2);

    @Nested
    class 알람_실행_시작 {

        @Test
        void 최초_울림을_포함해_실행을_시작하고_UUID를_발급한다() {
            // when
            AlarmOccurrence occurrence = startOccurrence();

            // then
            assertThat(occurrence.getOccurrenceUuid()).hasSize(36);
            assertThat(occurrence.getStartedAt()).isEqualTo(STARTED_AT);
            assertThat(occurrence.getRingings()).singleElement().satisfies(ringing -> {
                assertThat(ringing.getType()).isEqualTo(RingingType.INITIAL);
                assertThat(ringing.getEventId()).isNull();
                assertThat(ringing.getRingingAt()).isEqualTo(STARTED_AT);
                assertThat(ringing.getDismissedAt()).isNull();
            });
        }
    }

    @Nested
    class 재울림 {

        @Test
        void 처음_받은_eventId면_재울림을_추가한다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();

            // when
            occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(5));

            // then
            assertThat(occurrence.getRingings()).hasSize(2);
            assertThat(occurrence.getRingings().get(1).getType()).isEqualTo(RingingType.REPEAT);
            assertThat(occurrence.getRingings().get(1).getEventId()).isEqualTo("E1");
            assertThat(occurrence.getRingings().get(1).getRingingAt()).isEqualTo(STARTED_AT.plusMinutes(5));
        }

        @Test
        void 이미_받은_eventId면_추가하지_않고_처음_시각을_유지한다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(5));

            // when
            occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(6));

            // then
            assertThat(occurrence.getRingings()).hasSize(2);
            assertThat(occurrence.getRingings().get(1).getRingingAt()).isEqualTo(STARTED_AT.plusMinutes(5));
        }

        @Test
        void 종료된_실행에_새_재울림은_추가할_수_없다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(40));

            // when
            Throwable thrown = catchThrowable(() -> occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(41)));

            // then
            assertAlreadyEnded(thrown);
        }

        @Test
        void 종료된_실행이어도_이미_받은_eventId의_재전송은_무시한다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(5));
            occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(40));

            // when
            occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(41));

            // then
            assertThat(occurrence.getRingings()).hasSize(2);
        }
    }

    @Nested
    class 울림_끔 {

        @Test
        void eventId가_없으면_최초_울림을_끈다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();

            // when
            occurrence.dismiss(null, STARTED_AT.plusMinutes(1));

            // then
            assertThat(occurrence.getRingings().get(0).getDismissedAt()).isEqualTo(STARTED_AT.plusMinutes(1));
        }

        @Test
        void eventId가_있으면_해당_재울림을_끈다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.ringRepeat("E1", STARTED_AT.plusMinutes(5));

            // when
            occurrence.dismiss("E1", STARTED_AT.plusMinutes(6));

            // then
            assertThat(occurrence.getRingings().get(0).getDismissedAt()).isNull();
            assertThat(occurrence.getRingings().get(1).getDismissedAt()).isEqualTo(STARTED_AT.plusMinutes(6));
        }

        @Test
        void 이미_끈_울림은_처음_끈_시각을_유지한다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.dismiss(null, STARTED_AT.plusMinutes(1));

            // when
            occurrence.dismiss(null, STARTED_AT.plusMinutes(2));

            // then
            assertThat(occurrence.getRingings().get(0).getDismissedAt()).isEqualTo(STARTED_AT.plusMinutes(1));
        }

        @Test
        void 종료된_실행에서_끄지_않은_울림은_끌_수_없다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.end(OccurrenceEndType.FORCE_ENDED, STARTED_AT.plusMinutes(30));

            // when
            Throwable thrown = catchThrowable(() -> occurrence.dismiss(null, STARTED_AT.plusMinutes(31)));

            // then
            assertAlreadyEnded(thrown);
        }
    }

    @Nested
    class 실행_종료 {

        @Test
        void 도착하면_실행을_종료한다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();

            // when
            occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(40));

            // then
            assertThat(occurrence.getEndType()).isEqualTo(OccurrenceEndType.ARRIVED);
            assertThat(occurrence.getEndedAt()).isEqualTo(STARTED_AT.plusMinutes(40));
        }

        @Test
        void 같은_종료_요청의_재전송은_무시한다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(40));

            // when
            occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(40));

            // then
            assertThat(occurrence.getEndType()).isEqualTo(OccurrenceEndType.ARRIVED);
            assertThat(occurrence.getEndedAt()).isEqualTo(STARTED_AT.plusMinutes(40));
        }

        @Test
        void 이미_종료된_실행에_다른_종료_요청은_할_수_없다() {
            // given
            AlarmOccurrence occurrence = startOccurrence();
            occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(40));

            // when
            Throwable differentType = catchThrowable(
                () -> occurrence.end(OccurrenceEndType.FORCE_ENDED, STARTED_AT.plusMinutes(40)));
            Throwable differentTime = catchThrowable(
                () -> occurrence.end(OccurrenceEndType.ARRIVED, STARTED_AT.plusMinutes(41)));

            // then
            assertAlreadyEnded(differentType);
            assertAlreadyEnded(differentTime);
        }
    }

    private AlarmOccurrence startOccurrence() {
        return AlarmOccurrence.start(null, "alarm-1", LocalDateTime.of(2026, 9, 23, 7, 0), LocalTime.of(7, 0),
            STARTED_AT);
    }

    private void assertAlreadyEnded(Throwable thrown) {
        assertThat(thrown).isInstanceOf(GeneralException.class);
        assertThat(((GeneralException) thrown).getCode())
            .isEqualTo(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ALREADY_ENDED);
    }
}
