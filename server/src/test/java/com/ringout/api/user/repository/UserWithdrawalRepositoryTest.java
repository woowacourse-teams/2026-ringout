package com.ringout.api.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmDay;
import com.ringout.api.alarm.domain.AlarmSetting;
import com.ringout.api.alarm.repository.AlarmRepository;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.user.domain.User;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=none"
})
@Import({JpaAuditingConfig.class, UserWithdrawalRepository.class})
class UserWithdrawalRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 12, 0);

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private UserWithdrawalRepository userWithdrawalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 알람이_있는_회원을_삭제하면_회원과_알람이_함께_삭제된다() {
        // given
        User withdrawer = saveUser();
        saveAlarm(withdrawer, "alarm-a");
        saveAlarm(withdrawer, "alarm-b");
        alarmRepository.deleteOwnedAlarm(withdrawer.getId(), "alarm-b");
        flush();

        // when
        userWithdrawalRepository.deleteAllByUserId(withdrawer.getId());

        // then
        assertThat(countUsers(withdrawer.getId())).isZero();
        assertThat(countAlarms(withdrawer.getId())).isZero();
    }

    @Test
    void 다른_회원의_알람은_삭제하지_않는다() {
        // given
        User withdrawer = saveUser();
        User otherUser = saveUser();
        saveAlarm(withdrawer, "alarm-a");
        saveAlarm(otherUser, "alarm-a");
        flush();

        // when
        userWithdrawalRepository.deleteAllByUserId(withdrawer.getId());

        // then
        assertThat(countUsers(otherUser.getId())).isEqualTo(1);
        assertThat(countAlarms(otherUser.getId())).isEqualTo(1);
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "withdrawal-" + sequence.incrementAndGet(), null, NOW)
        );
    }

    private void saveAlarm(User user, String alarmId) {
        AlarmSetting setting = AlarmSetting.of(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "세종대학교",
            37.5502, 127.0738);
        alarmRepository.save(Alarm.create(user, alarmId, setting, 1L));
    }

    private void flush() {
        userRepository.flush();
    }

    private int countUsers(Long userId) {
        return jdbcTemplate.queryForObject("select count(*) from `user` where id = ?", Integer.class, userId);
    }

    private int countAlarms(Long userId) {
        return jdbcTemplate.queryForObject("select count(*) from alarm where user_id = ?", Integer.class, userId);
    }
}
