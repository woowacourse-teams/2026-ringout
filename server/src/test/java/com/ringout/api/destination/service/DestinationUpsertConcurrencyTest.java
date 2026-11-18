package com.ringout.api.destination.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.dto.request.DestinationUpsertRequest;
import com.ringout.api.destination.dto.response.DestinationUpsertResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
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
@Import({JpaAuditingConfig.class, ClockConfig.class, DestinationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DestinationUpsertConcurrencyTest {

    private static final int REQUEST_COUNT = 8;
    private static final int REPEAT_COUNT = 10;

    @Autowired
    private DestinationService destinationService;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        destinationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 같은_목적지를_동시에_생성해도_하나만_생성되고_오류가_발생하지_않는다() throws Exception {
        // given
        User user = saveUser("destination-upsert-create");

        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            String destinationId = "dest-" + repeat;

            // when
            List<Future<DestinationUpsertResponse>> results = runConcurrently(version ->
                destinationService.upsertDestination(user.getId(), destinationId, request(version)));

            // then
            assertThat(getAll(results)).hasSize(REQUEST_COUNT);
            assertThat(destinationRepository.findOwnedDestinations(user.getId()))
                .filteredOn(destination -> destination.getDestinationId().equals(destinationId))
                .singleElement()
                .extracting(Destination::getDestinationVersion)
                .isEqualTo((long) REQUEST_COUNT);
        }
    }

    @Test
    void 서로_다른_버전으로_동시에_수정해도_가장_높은_버전이_최종_저장된다() throws Exception {
        // given
        User user = saveUser("destination-upsert-update");

        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            String destinationId = "dest-" + repeat;
            destinationService.upsertDestination(user.getId(), destinationId,
                new DestinationUpsertRequest("초기", 37.0, 127.0, 0L));

            // when
            getAll(runConcurrently(version ->
                destinationService.upsertDestination(user.getId(), destinationId, request(version))));

            // then
            Destination destination = destinationRepository.findOwnedDestinations(user.getId()).stream()
                .filter(saved -> saved.getDestinationId().equals(destinationId))
                .findFirst()
                .orElseThrow();
            assertThat(destination.getDestinationVersion()).isEqualTo((long) REQUEST_COUNT);
            assertThat(destination.getAlias().getValue()).isEqualTo("v" + REQUEST_COUNT);
        }
    }

    private DestinationUpsertRequest request(long version) {
        return new DestinationUpsertRequest("v" + version, 37.0 + version * 0.01, 127.0, version);
    }

    private User saveUser(String socialId) {
        return userRepository.save(User.register(SocialProvider.KAKAO, socialId, null, LocalDateTime.now()));
    }

    private List<Future<DestinationUpsertResponse>> runConcurrently(VersionedUpsert upsert) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DestinationUpsertResponse>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int request = 1; request <= REQUEST_COUNT; request++) {
                long version = request;
                Callable<DestinationUpsertResponse> task = () -> {
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

    private List<DestinationUpsertResponse> getAll(List<Future<DestinationUpsertResponse>> futures) throws Exception {
        List<DestinationUpsertResponse> results = new ArrayList<>();
        for (Future<DestinationUpsertResponse> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    @FunctionalInterface
    private interface VersionedUpsert {

        DestinationUpsertResponse run(long version);
    }
}
