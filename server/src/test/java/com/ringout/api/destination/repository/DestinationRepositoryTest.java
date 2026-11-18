package com.ringout.api.destination.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

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
class DestinationRepositoryTest {

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 소유자의_목적지를_삭제한다() {
        // given
        User owner = saveUser();
        saveDestination(owner, "dest-1");

        // when
        destinationRepository.deleteOwnedDestination(owner.getId(), "dest-1");

        // then
        assertThat(destinationRepository.findOwnedDestinations(owner.getId())).isEmpty();
    }

    @Test
    void 이미_삭제된_목적지를_다시_삭제해도_오류가_발생하지_않는다() {
        // given
        User owner = saveUser();
        saveDestination(owner, "dest-1");
        destinationRepository.deleteOwnedDestination(owner.getId(), "dest-1");

        // when
        destinationRepository.deleteOwnedDestination(owner.getId(), "dest-1");

        // then
        assertThat(destinationRepository.findOwnedDestinations(owner.getId())).isEmpty();
    }

    @Test
    void 같은_목적지_id를_가진_다른_사용자의_목적지는_삭제하지_않는다() {
        // given
        User owner = saveUser();
        User otherUser = saveUser();
        saveDestination(owner, "same");
        saveDestination(otherUser, "same");

        // when
        destinationRepository.deleteOwnedDestination(owner.getId(), "same");

        // then
        assertThat(destinationRepository.findOwnedDestinations(owner.getId())).isEmpty();
        assertThat(destinationRepository.findOwnedDestinations(otherUser.getId()))
            .extracting(Destination::getDestinationId)
            .containsExactly("same");
    }

    @Test
    void 다른_사용자의_목적지는_소유_목적지로_조회하지_않는다() {
        // given
        User owner = saveUser();
        User otherUser = saveUser();
        saveDestination(owner, "dest-1");

        // when
        Optional<Destination> destination = destinationRepository.findOwnedDestinationForUpdate(otherUser.getId(), "dest-1");

        // then
        assertThat(destination).isEmpty();
    }

    @Test
    void 한_사용자_안에서_목적지_id는_중복될_수_없다() {
        // given
        User owner = saveUser();
        saveDestination(owner, "same");

        // when // then
        assertThatThrownBy(() -> saveDestination(owner, "same"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Nested
    class 소유_목적지_목록_조회 {

        @Test
        void 소유자의_목적지만_목적지_id_오름차순으로_조회한다() {
            // given
            User owner = saveUser();
            User otherUser = saveUser();
            saveDestination(owner, "destination-003");
            saveDestination(owner, "destination-001");
            saveDestination(otherUser, "destination-000");
            saveDestination(owner, "destination-002");

            // when
            List<Destination> destinations = destinationRepository.findOwnedDestinations(owner.getId());

            // then
            assertThat(destinations)
                .extracting(Destination::getDestinationId)
                .containsExactly("destination-001", "destination-002", "destination-003");
        }

        @Test
        void 목적지가_없으면_빈_목록을_조회한다() {
            // given
            User owner = saveUser();

            // when
            List<Destination> destinations = destinationRepository.findOwnedDestinations(owner.getId());

            // then
            assertThat(destinations).isEmpty();
        }
    }

    @Nested
    class 목적지_upsert {

        private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

        private Destination upsert(User user, String alias, double latitude, double longitude, long version) {
            destinationRepository.upsertOwnedDestination(user.getId(), "destination-001", alias, latitude, longitude,
                version, NOW);
            return destinationRepository.findOwnedDestinationForUpdate(user.getId(), "destination-001").orElseThrow();
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 3L, 10L})
        void 목적지가_없으면_요청_버전_그대로_생성한다(long version) {
            // given
            User owner = saveUser();

            // when
            Destination destination = upsert(owner, "헬스장", 37.4979, 127.0276, version);

            // then
            assertThat(destination.getAlias().getValue()).isEqualTo("헬스장");
            assertThat(destination.getCoordinate().getLatitude()).isEqualTo(37.4979);
            assertThat(destination.getCoordinate().getLongitude()).isEqualTo(127.0276);
            assertThat(destination.getDestinationVersion()).isEqualTo(version);
            assertThat(destination.getCreated_at()).isEqualTo(NOW);
        }

        @ParameterizedTest
        @CsvSource({"1, 2", "3, 10"})
        void 요청_버전이_서버_버전보다_높으면_전체_정보를_교체한다(long serverVersion, long requestVersion) {
            // given
            User owner = saveUser();
            upsert(owner, "학교", 37.5502, 127.0738, serverVersion);

            // when
            Destination destination = upsert(owner, "회사", 37.5665, 126.9780, requestVersion);

            // then
            assertThat(destination.getAlias().getValue()).isEqualTo("회사");
            assertThat(destination.getCoordinate().getLatitude()).isEqualTo(37.5665);
            assertThat(destination.getCoordinate().getLongitude()).isEqualTo(126.9780);
            assertThat(destination.getDestinationVersion()).isEqualTo(requestVersion);
            assertThat(destinationRepository.findOwnedDestinations(owner.getId())).hasSize(1);
        }

        @ParameterizedTest
        @CsvSource({"3, 3", "5, 2"})
        void 요청_버전이_서버_버전보다_낮거나_같으면_어떤_필드도_바꾸지_않는다(long serverVersion, long requestVersion) {
            // given
            User owner = saveUser();
            upsert(owner, "학교", 37.5502, 127.0738, serverVersion);

            // when
            Destination destination = upsert(owner, "회사", 37.5665, 126.9780, requestVersion);

            // then
            assertThat(destination.getAlias().getValue()).isEqualTo("학교");
            assertThat(destination.getCoordinate().getLatitude()).isEqualTo(37.5502);
            assertThat(destination.getCoordinate().getLongitude()).isEqualTo(127.0738);
            assertThat(destination.getDestinationVersion()).isEqualTo(serverVersion);
        }

        @Test
        void 다른_사용자에게만_같은_목적지_id가_있으면_내_목적지를_새로_생성한다() {
            // given
            User owner = saveUser();
            User otherUser = saveUser();
            upsert(owner, "학교", 37.5502, 127.0738, 5L);

            // when
            Destination created = upsert(otherUser, "회사", 37.5665, 126.9780, 1L);

            // then
            assertThat(created.getDestinationVersion()).isEqualTo(1L);
            Destination ownersDestination = destinationRepository.findOwnedDestinationForUpdate(owner.getId(),
                "destination-001").orElseThrow();
            assertThat(ownersDestination.getAlias().getValue()).isEqualTo("학교");
            assertThat(ownersDestination.getDestinationVersion()).isEqualTo(5L);
        }
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "destination-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Destination saveDestination(User user, String destinationId) {
        return destinationRepository.save(Destination.create(
            user, destinationId, DestinationAlias.from("집"), Coordinate.of(37.5, 127.0)
        ));
    }
}
