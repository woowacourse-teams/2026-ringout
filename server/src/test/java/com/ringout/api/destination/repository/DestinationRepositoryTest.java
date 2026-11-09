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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
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
        Optional<Destination> destination = destinationRepository.findOwnedDestination(otherUser.getId(), "dest-1");

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
