package com.ringout.api.alarmoccurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
class AlarmOccurrenceRepositoryTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 1, 7, 0);

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private AlarmOccurrenceRepository alarmOccurrenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 사용자의_삭제되지_않은_도착_종료_시각을_오름차순으로_조회한다() {
        // given
        User owner = saveUser();
        User otherUser = saveUser();
        saveEndedOccurrence(owner, BASE_TIME.plusDays(2), OccurrenceEndType.ARRIVED);
        saveEndedOccurrence(owner, BASE_TIME, OccurrenceEndType.ARRIVED);
        saveEndedOccurrence(owner, BASE_TIME.plusDays(1), OccurrenceEndType.FORCE_ENDED);
        saveOccurrence(owner, BASE_TIME.plusDays(3));
        AlarmOccurrence deleted = saveEndedOccurrence(owner, BASE_TIME.plusDays(4), OccurrenceEndType.ARRIVED);
        saveEndedOccurrence(otherUser, BASE_TIME.plusDays(5), OccurrenceEndType.ARRIVED);
        jdbcTemplate.update("update alarm_occurrence set deleted_at = ? where id = ?", BASE_TIME, deleted.getId());

        // when
        List<LocalDateTime> arrivedAts = alarmOccurrenceRepository.findActiveEndedAtsByUserIdAndEndType(
            owner.getId(), OccurrenceEndType.ARRIVED);

        // then
        assertThat(arrivedAts).containsExactly(
            BASE_TIME.plusMinutes(30),
            BASE_TIME.plusDays(2).plusMinutes(30)
        );
    }

    private User saveUser() {
        return userRepository.save(User.register(SocialProvider.KAKAO, "arrival-user-" + sequence.incrementAndGet(),
            null, BASE_TIME));
    }

    private AlarmOccurrence saveOccurrence(User user, LocalDateTime startedAt) {
        return alarmOccurrenceRepository.saveAndFlush(AlarmOccurrence.start(user, "alarm-" + sequence.incrementAndGet(),
            startedAt, startedAt.toLocalTime(), startedAt));
    }

    private AlarmOccurrence saveEndedOccurrence(User user, LocalDateTime startedAt, OccurrenceEndType endType) {
        AlarmOccurrence occurrence = AlarmOccurrence.start(user, "alarm-" + sequence.incrementAndGet(), startedAt,
            startedAt.toLocalTime(), startedAt);
        occurrence.end(endType, startedAt.plusMinutes(30));
        return alarmOccurrenceRepository.saveAndFlush(occurrence);
    }
}
