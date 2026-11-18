package com.ringout.api.destination.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
class DestinationDeleteConcurrencyTest {

    private static final int REQUEST_COUNT = 8;
    private static final int REPEAT_COUNT = 20;

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
    void 같은_목적지를_동시에_삭제해도_오류가_발생하지_않는다() throws Exception {
        // given
        User user = userRepository.save(
            User.register(SocialProvider.KAKAO, "destination-delete", null, LocalDateTime.now())
        );

        // when
        List<Throwable> failures = new ArrayList<>();
        for (int repeat = 0; repeat < REPEAT_COUNT; repeat++) {
            Destination destination = destinationRepository.save(Destination.create(
                user, "dest-" + repeat, DestinationAlias.from("집"), Coordinate.of(37.5, 127.0)
            ));
            failures.addAll(deleteConcurrently(user.getId(), destination.getDestinationId()));
        }

        // then
        assertThat(failures).isEmpty();
        assertThat(destinationRepository.findOwnedDestinations(user.getId())).isEmpty();
    }

    private List<Throwable> deleteConcurrently(Long userId, String destinationId) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int request = 0; request < REQUEST_COUNT; request++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return deleteAndCaptureFailure(userId, destinationId);
                }));
            }
            ready.await();
            start.countDown();
        }

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> result : results) {
            Throwable failure = result.get();
            if (failure != null) {
                failures.add(failure);
            }
        }
        return failures;
    }

    private Throwable deleteAndCaptureFailure(Long userId, String destinationId) {
        try {
            destinationService.deleteDestination(userId, destinationId);
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }
}
