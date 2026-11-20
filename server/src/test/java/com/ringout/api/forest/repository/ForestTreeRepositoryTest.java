package com.ringout.api.forest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.forest.domain.ForestTree;
import com.ringout.api.forest.domain.TreeKind;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

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
class ForestTreeRepositoryTest {

    private static final LocalDateTime PLANTED_AT = LocalDateTime.of(2026, 11, 5, 7, 48, 10);

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private ForestTreeRepository forestTreeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 사용자의_나무를_회차_오름차순으로_조회한다() {
        // given
        User owner = saveUser();
        User otherUser = saveUser();
        forestTreeRepository.save(ForestTree.plant(owner, 3, TreeKind.OAK, 1, PLANTED_AT));
        forestTreeRepository.save(ForestTree.plant(owner, 1, TreeKind.PINE, 7, PLANTED_AT));
        forestTreeRepository.save(ForestTree.plant(owner, 2, TreeKind.BIG_TREE, 12, PLANTED_AT));
        forestTreeRepository.save(ForestTree.plant(otherUser, 4, TreeKind.PINE, 0, PLANTED_AT));

        // when
        List<ForestTree> trees = forestTreeRepository.findAllByUserIdOrderByGrowthCycle(owner.getId());

        // then
        assertThat(trees).extracting(ForestTree::getGrowthCycle).containsExactly(1, 2, 3);
    }

    private User saveUser() {
        return userRepository.save(User.register(SocialProvider.KAKAO, "forest-tree-" + sequence.incrementAndGet(),
            null, PLANTED_AT));
    }
}
