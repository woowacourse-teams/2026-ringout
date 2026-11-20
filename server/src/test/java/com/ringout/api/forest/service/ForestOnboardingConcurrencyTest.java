package com.ringout.api.forest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingCompleteResult;
import com.ringout.api.forest.repository.ForestOnboardingRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
@Import({JpaAuditingConfig.class, ClockConfig.class, ForestService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ForestOnboardingConcurrencyTest {

    private static final int REQUEST_COUNT = 8;
    private static final int REPEAT_COUNT = 10;
    private static final OffsetDateTime FIRST_COMPLETED_AT = OffsetDateTime.parse("2026-11-10T08:00:00+09:00");

    @Autowired
    private ForestService forestService;

    @Autowired
    private ForestOnboardingRepository forestOnboardingRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        forestOnboardingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 같은_사용자가_동시에_온보딩을_완료해도_하나만_저장되고_한_요청만_생성으로_응답한다() throws Exception {
        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            // given
            User user = saveUser("forest-onboarding-" + repeat);

            // when
            List<ForestOnboardingCompleteResult> results = getAll(runConcurrently(order ->
                forestService.completeOnboarding(user.getId(),
                    new ForestOnboardingCompleteRequest(FIRST_COMPLETED_AT.plusMinutes(order)))));

            // then
            assertThat(results).filteredOn(ForestOnboardingCompleteResult::created).hasSize(1);
            assertThat(results)
                .extracting(result -> result.response().onboardingCompletedAt())
                .containsOnly(results.stream()
                    .filter(ForestOnboardingCompleteResult::created)
                    .findFirst()
                    .orElseThrow()
                    .response()
                    .onboardingCompletedAt());
            assertThat(forestOnboardingRepository.findByUserId(user.getId())).isPresent();
        }
    }

    private User saveUser(String socialId) {
        return userRepository.save(User.register(SocialProvider.KAKAO, socialId, null, LocalDateTime.now()));
    }

    private List<Future<ForestOnboardingCompleteResult>> runConcurrently(OrderedRequest request) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ForestOnboardingCompleteResult>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int order = 0; order < REQUEST_COUNT; order++) {
                int requestOrder = order;
                Callable<ForestOnboardingCompleteResult> task = () -> {
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

    private List<ForestOnboardingCompleteResult> getAll(List<Future<ForestOnboardingCompleteResult>> futures)
        throws Exception {
        List<ForestOnboardingCompleteResult> results = new ArrayList<>();
        for (Future<ForestOnboardingCompleteResult> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    @FunctionalInterface
    private interface OrderedRequest {

        ForestOnboardingCompleteResult run(int order);
    }
}
