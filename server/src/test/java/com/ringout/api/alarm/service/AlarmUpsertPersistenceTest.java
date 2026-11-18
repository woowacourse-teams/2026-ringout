package com.ringout.api.alarm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmDay;
import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.repository.AlarmRepository;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.test.database.replace=none",
    "spring.datasource.url=jdbc:tc:mysql:8.4:///ringout",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({JpaAuditingConfig.class, ClockConfig.class, AlarmService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AlarmUpsertPersistenceTest {

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        alarmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 낮거나_같은_버전으로_upsert하면_수정_시각도_바뀌지_않는다() throws Exception {
        // given
        User user = userRepository.save(User.register(SocialProvider.KAKAO, "alarm-persist", null,
            LocalDateTime.now()));
        alarmService.upsertAlarm(user.getId(), "alarm-001", request(3L));
        LocalDateTime updatedAt = findAlarm().getUpdated_at();
        Thread.sleep(1100);

        // when
        alarmService.upsertAlarm(user.getId(), "alarm-001", request(3L));
        alarmService.upsertAlarm(user.getId(), "alarm-001", request(2L));

        // then
        Alarm alarm = findAlarm();
        assertThat(alarm.version()).isEqualTo(3L);
        assertThat(alarm.getUpdated_at()).isEqualTo(updatedAt);
    }

    private Alarm findAlarm() {
        return alarmRepository.findAll().get(0);
    }

    private AlarmUpsertRequest request(long version) {
        return new AlarmUpsertRequest(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), 5, "세종대학교", 37.5502, 127.0738,
            version);
    }
}
