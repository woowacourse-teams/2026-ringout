package com.ringout.api.room.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
class RoomRepositoryTest {

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 활동_요일과_시각이_일치하는_방만_조회한다() {
        // given
        User host = saveUser();
        Room target = saveRoom(host, List.of(ActivityDay.MONDAY, ActivityDay.WEDNESDAY), LocalTime.of(9, 0));
        saveRoom(host, List.of(ActivityDay.TUESDAY), LocalTime.of(9, 0));
        saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(10, 0));

        // when
        List<Room> rooms = roomRepository.findActiveByActivityDayAndTime(ActivityDay.MONDAY.getMask(), 9, 0);

        // then
        assertThat(rooms).extracting(Room::getId).containsExactly(target.getId());
    }

    @Test
    void 활동_시각에_초가_있어도_분_단위로_일치하면_조회한다() {
        // given
        User host = saveUser();
        Room target = saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0, 30));

        // when
        List<Room> rooms = roomRepository.findActiveByActivityDayAndTime(ActivityDay.MONDAY.getMask(), 9, 0);

        // then
        assertThat(rooms).extracting(Room::getId).containsExactly(target.getId());
    }

    @Test
    void 삭제된_방은_조회하지_않는다() {
        // given
        User host = saveUser();
        Room deleted = saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0));
        deleted.softDelete();
        roomRepository.flush();

        // when
        List<Room> rooms = roomRepository.findActiveByActivityDayAndTime(ActivityDay.MONDAY.getMask(), 9, 0);

        // then
        assertThat(rooms).isEmpty();
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "rr-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Room saveRoom(User host, List<ActivityDay> activityDays, LocalTime activityTime) {
        return roomRepository.save(Room.of(
            host, null, "모임 " + sequence.incrementAndGet(), null, activityDays, activityTime
        ));
    }
}
