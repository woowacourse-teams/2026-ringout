package com.ringout.api.forest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.alarmoccurrence.repository.AlarmRingingRepository;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.forest.domain.ForestOnboarding;
import com.ringout.api.forest.domain.TreeKind;
import com.ringout.api.forest.dto.request.ForestTreeAcknowledgeRequest;
import com.ringout.api.forest.dto.request.ForestTreePlantRequest;
import com.ringout.api.forest.dto.response.ForestTreeAcknowledgeResult;
import com.ringout.api.forest.dto.response.ForestTreePlantResult;
import com.ringout.api.forest.repository.ForestOnboardingRepository;
import com.ringout.api.forest.repository.ForestTreeRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
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
@Import({JpaAuditingConfig.class, ClockConfig.class, ForestTreeService.class, ForestTreeSlotAllocator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ForestTreeConcurrencyTest {

    private static final int REQUEST_COUNT = 8;
    private static final int REPEAT_COUNT = 5;
    private static final LocalDate FIRST_ARRIVED_DAY = LocalDate.of(2026, 1, 1);
    private static final int ARRIVED_DAYS = REQUEST_COUNT * 5;

    @Autowired
    private ForestTreeService forestTreeService;

    @Autowired
    private ForestTreeRepository forestTreeRepository;

    @Autowired
    private ForestOnboardingRepository forestOnboardingRepository;

    @Autowired
    private AlarmOccurrenceRepository alarmOccurrenceRepository;

    @Autowired
    private AlarmRingingRepository alarmRingingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @AfterEach
    void tearDown() {
        forestOnboardingRepository.deleteAll();
        forestTreeRepository.deleteAll();
        alarmRingingRepository.deleteAll();
        alarmOccurrenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 같은_회차를_동시에_심어도_하나만_저장되고_한_요청만_생성으로_응답한다() throws Exception {
        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            // given
            User user = saveUserWithArrivedDays("forest-tree-same-" + repeat);

            // when
            List<ForestTreePlantResult> results = getAll(runConcurrently(order ->
                forestTreeService.plantTree(user.getId(), "1", request(1, TreeKind.values()[order % 3]))));

            // then
            assertThat(results).filteredOn(ForestTreePlantResult::created).hasSize(1);
            assertThat(results)
                .extracting(result -> result.response().slotIndex())
                .containsOnly(results.getFirst().response().slotIndex());
            assertThat(forestTreeRepository.findSlotIndexesByUserId(user.getId())).hasSize(1);
        }
    }

    @Test
    void 서로_다른_회차를_동시에_심어도_칸이_겹치지_않는다() throws Exception {
        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            // given
            User user = saveUserWithArrivedDays("forest-tree-different-" + repeat);

            // when
            List<ForestTreePlantResult> results = getAll(runConcurrently(order ->
                forestTreeService.plantTree(user.getId(), String.valueOf(order + 1),
                    request(order + 1, TreeKind.PINE))));

            // then
            assertThat(results).allMatch(ForestTreePlantResult::created);
            assertThat(forestTreeRepository.findSlotIndexesByUserId(user.getId()))
                .hasSize(REQUEST_COUNT)
                .doesNotHaveDuplicates();
        }
    }

    @Test
    void 같은_나무를_동시에_확인해도_처음_시각만_저장되고_한_요청만_확인으로_응답한다() throws Exception {
        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            // given
            User user = saveUserWithArrivedDays("forest-tree-acknowledge-" + repeat);
            forestTreeService.plantTree(user.getId(), "1", request(1, TreeKind.PINE));
            forestOnboardingRepository.save(ForestOnboarding.complete(user, LocalDateTime.now(clock)));
            OffsetDateTime firstAcknowledgedAt = arrivedAt(10).toOffsetDateTime();

            // when
            List<ForestTreeAcknowledgeResult> results = getAll(runConcurrently(order ->
                forestTreeService.acknowledgeTree(user.getId(), "1",
                    new ForestTreeAcknowledgeRequest(firstAcknowledgedAt.plusMinutes(order)))));

            // then
            assertThat(results).filteredOn(ForestTreeAcknowledgeResult::acknowledged).hasSize(1);
            OffsetDateTime savedAcknowledgedAt = results.stream()
                .filter(ForestTreeAcknowledgeResult::acknowledged)
                .findFirst()
                .orElseThrow()
                .response()
                .acknowledgedAt();
            assertThat(results)
                .extracting(result -> result.response().acknowledgedAt())
                .allMatch(acknowledgedAt -> acknowledgedAt.isEqual(savedAcknowledgedAt));
        }
    }

    private ForestTreePlantRequest request(int growthCycle, TreeKind kind) {
        return new ForestTreePlantRequest(kind, arrivedAt(growthCycle * 5 - 1).toOffsetDateTime());
    }

    private ZonedDateTime arrivedAt(int dayIndex) {
        return FIRST_ARRIVED_DAY.plusDays(dayIndex).atTime(7, 30).atZone(clock.getZone());
    }

    private User saveUserWithArrivedDays(String socialId) {
        User user = userRepository.save(User.register(SocialProvider.KAKAO, socialId, null, LocalDateTime.now()));
        for (int day = 0; day < ARRIVED_DAYS; day++) {
            LocalDateTime arrivedAt = arrivedAt(day).toLocalDateTime();
            LocalDateTime startedAt = arrivedAt.minusMinutes(30);
            AlarmOccurrence occurrence = AlarmOccurrence.start(user, "alarm-" + day, startedAt,
                startedAt.toLocalTime(), startedAt);
            occurrence.end(OccurrenceEndType.ARRIVED, arrivedAt);
            alarmOccurrenceRepository.save(occurrence);
        }
        return user;
    }

    private <T> List<Future<T>> runConcurrently(OrderedRequest<T> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int order = 0; order < REQUEST_COUNT; order++) {
                int requestOrder = order;
                Callable<T> task = () -> {
                    ready.countDown();
                    start.await();
                    return request.run(requestOrder);
                };
                results.add(executor.submit(task));
            }
            ready.await();
            start.countDown();
        }
        return results;
    }

    private <T> List<T> getAll(List<Future<T>> futures) throws Exception {
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    @FunctionalInterface
    private interface OrderedRequest<T> {

        T run(int order);
    }
}
