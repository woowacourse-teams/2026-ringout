package com.ringout.api.alarm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmDay;
import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.dto.response.AlarmResponse;
import com.ringout.api.alarm.repository.AlarmRepository;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class AlarmUpsertConcurrencyTest {

    private static final int REQUEST_COUNT = 8;
    private static final int REPEAT_COUNT = 10;

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
    void 같은_알람을_동시에_생성해도_하나만_생성되고_가장_높은_버전이_저장된다() throws Exception {
        // given
        User user = saveUser("alarm-upsert-create");

        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            String alarmId = "alarm-" + repeat;

            // when
            List<AlarmResponse> responses = getAll(runConcurrently(version ->
                alarmService.upsertAlarm(user.getId(), alarmId, request(version))));

            // then
            assertThat(responses).hasSize(REQUEST_COUNT);
            List<Alarm> alarms = alarmRepository.findAll().stream()
                .filter(alarm -> alarm.alarmId().equals(alarmId))
                .toList();
            assertThat(alarms).singleElement()
                .satisfies(alarm -> {
                    assertThat(alarm.version()).isEqualTo(REQUEST_COUNT);
                    assertThat(alarm.repeatMinutes()).isEqualTo(REQUEST_COUNT);
                });
        }
    }

    @Test
    void 서로_다른_버전으로_동시에_수정해도_가장_높은_버전이_최종_저장된다() throws Exception {
        // given
        User user = saveUser("alarm-upsert-update");

        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            String alarmId = "alarm-" + repeat;
            alarmService.upsertAlarm(user.getId(), alarmId, request(0));

            // when
            getAll(runConcurrently(version -> alarmService.upsertAlarm(user.getId(), alarmId, request(version))));

            // then
            Alarm alarm = alarmRepository.findAll().stream()
                .filter(saved -> saved.alarmId().equals(alarmId))
                .findFirst()
                .orElseThrow();
            assertThat(alarm.version()).isEqualTo(REQUEST_COUNT);
            assertThat(alarm.repeatMinutes()).isEqualTo(REQUEST_COUNT);
        }
    }

    private AlarmUpsertRequest request(long version) {
        return new AlarmUpsertRequest(LocalTime.of(7, 0), List.of(AlarmDay.MONDAY), (int) version, "세종대학교",
            37.5502, 127.0738, version);
    }

    private User saveUser(String socialId) {
        return userRepository.save(User.register(SocialProvider.KAKAO, socialId, null, LocalDateTime.now()));
    }

    private List<Future<AlarmResponse>> runConcurrently(VersionedUpsert upsert) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AlarmResponse>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int request = 1; request <= REQUEST_COUNT; request++) {
                long version = request;
                Callable<AlarmResponse> task = () -> {
                    ready.countDown();
                    start.await();
                    return upsert.run(version);
                };
                results.add(executor.submit(task));
            }
            ready.await();
            start.countDown();
        }
        return results;
    }

    private List<AlarmResponse> getAll(List<Future<AlarmResponse>> futures) throws Exception {
        List<AlarmResponse> responses = new ArrayList<>();
        for (Future<AlarmResponse> future : futures) {
            responses.add(future.get());
        }
        return responses;
    }

    @FunctionalInterface
    private interface VersionedUpsert {

        AlarmResponse run(long version);
    }
}
