package com.ringout.api.alarm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmDay;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.test.database.replace=none",
    "spring.datasource.url=jdbc:tc:mysql:8.4:///ringout",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaAuditingConfig.class)
class AlarmRepositoryTest {

    private static final String ALARM_ID = "alarm-001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private UserRepository userRepository;

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "alarm-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Alarm upsert(User user, LocalTime time, int daysMask, int repeatMinutes, String name, long version) {
        alarmRepository.upsertOwnedAlarm(user.getId(), ALARM_ID, time, daysMask, repeatMinutes, name, 37.5502,
            127.0738, version, NOW);
        return alarmRepository.findOwnedAlarmForUpdate(user.getId(), ALARM_ID).orElseThrow();
    }

    @Nested
    class 알람_upsert {

        @ParameterizedTest
        @ValueSource(longs = {1L, 3L, 10L})
        void 알람이_없으면_요청_버전_그대로_생성한다(long version) {
            // given
            User owner = saveUser();

            // when
            Alarm alarm = upsert(owner, LocalTime.of(7, 0), 64 + 4, 5, "세종대학교", version);

            // then
            assertThat(alarm.alarmId()).isEqualTo(ALARM_ID);
            assertThat(alarm.alarmTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(alarm.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.FRIDAY);
            assertThat(alarm.repeatMinutes()).isEqualTo(5);
            assertThat(alarm.destinationName()).isEqualTo("세종대학교");
            assertThat(alarm.latitude()).isEqualTo(37.5502);
            assertThat(alarm.longitude()).isEqualTo(127.0738);
            assertThat(alarm.version()).isEqualTo(version);
            assertThat(alarm.getCreated_at()).isEqualTo(NOW);
        }

        @ParameterizedTest
        @CsvSource({"1, 2", "3, 10"})
        void 요청_버전이_서버_버전보다_높으면_전체_설정을_교체한다(long serverVersion, long requestVersion) {
            // given
            User owner = saveUser();
            upsert(owner, LocalTime.of(7, 0), 64, 5, "세종대학교", serverVersion);

            // when
            Alarm alarm = upsert(owner, LocalTime.of(8, 0), 64 + 4, 10, "우아한테크코스", requestVersion);

            // then
            assertThat(alarm.alarmTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(alarm.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.FRIDAY);
            assertThat(alarm.repeatMinutes()).isEqualTo(10);
            assertThat(alarm.destinationName()).isEqualTo("우아한테크코스");
            assertThat(alarm.version()).isEqualTo(requestVersion);
        }

        @ParameterizedTest
        @CsvSource({"3, 3", "5, 2"})
        void 요청_버전이_서버_버전보다_낮거나_같으면_어떤_필드도_바꾸지_않는다(long serverVersion, long requestVersion) {
            // given
            User owner = saveUser();
            upsert(owner, LocalTime.of(9, 0), 64 + 32 + 4, 5, "세종대학교", serverVersion);

            // when
            Alarm alarm = upsert(owner, LocalTime.of(8, 0), 1, 10, "우아한테크코스", requestVersion);

            // then
            assertThat(alarm.alarmTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(alarm.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.TUESDAY, AlarmDay.FRIDAY);
            assertThat(alarm.repeatMinutes()).isEqualTo(5);
            assertThat(alarm.destinationName()).isEqualTo("세종대학교");
            assertThat(alarm.version()).isEqualTo(serverVersion);
        }

        @Test
        void 요일이_없는_알람은_빈_요일_목록으로_저장한다() {
            // given
            User owner = saveUser();

            // when
            Alarm alarm = upsert(owner, LocalTime.of(7, 0), 0, 0, "세종대학교", 1L);

            // then
            assertThat(alarm.days()).isEmpty();
        }

        @Test
        void 다른_사용자에게만_같은_알람_ID가_있으면_현재_사용자의_알람을_새로_생성한다() {
            // given
            User owner = saveUser();
            User otherUser = saveUser();
            upsert(owner, LocalTime.of(9, 0), 64, 5, "세종대학교", 5L);

            // when
            Alarm created = upsert(otherUser, LocalTime.of(7, 0), 4, 10, "우아한테크코스", 1L);

            // then
            assertThat(created.version()).isEqualTo(1L);
            Alarm ownersAlarm = alarmRepository.findOwnedAlarmForUpdate(owner.getId(), ALARM_ID).orElseThrow();
            assertThat(ownersAlarm.alarmTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(ownersAlarm.version()).isEqualTo(5L);
        }
    }
}
