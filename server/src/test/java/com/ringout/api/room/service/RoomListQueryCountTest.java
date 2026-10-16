package com.ringout.api.room.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomSummaryResponse;
import com.ringout.api.room.repository.RoomBlackListRepository;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
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
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import(JpaAuditingConfig.class)
public class RoomListQueryCountTest {

    private static final int MAX_QUERIES_PER_ROOM_LIST = 2;

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomUserRepository roomUserRepository;

    @Autowired
    private RoomBlackListRepository roomBlackListRepository;

    @Autowired
    private UserRepository userRepository;

    private RoomService roomService;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        // 실제 구현처럼 이미지 엔티티의 url 을 읽게 해서, 이미지를 따로 조회하면(LAZY 초기화) 쿼리 수에 잡히게 한다.
        ImageFileService imageFileService = mock(ImageFileService.class);
        BDDMockito.given(imageFileService.createReadUri(any(ImageFile.class)))
            .willAnswer(invocation -> URI.create(invocation.<ImageFile>getArgument(0).getUrl()));

        roomService = new RoomService(
            roomRepository, roomUserRepository, roomBlackListRepository, userRepository, imageFileService
        );
        statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Nested
    class 쿼리_수 {

        @Test
        void 로그인_사용자의_방_목록_조회는_방_개수와_무관하게_쿼리_수가_일정하다() {
            // given
            User viewer = saveUser();
            saveRooms(3, viewer);
            long queriesWith3Rooms = countQueries(() -> roomService.getRooms(viewer.getId()));

            saveRooms(27, viewer);

            // when
            long queriesWith30Rooms = countQueries(() -> roomService.getRooms(viewer.getId()));

            // then
            assertThat(queriesWith30Rooms)
                .as("방 3개 → 30개로 늘어도 쿼리 수가 같아야 한다 (N+1 이면 방 수에 비례해 늘어난다)")
                .isEqualTo(queriesWith3Rooms)
                .isLessThanOrEqualTo(MAX_QUERIES_PER_ROOM_LIST);
        }

        @Test
        void 비로그인_사용자의_방_목록_조회도_방_개수와_무관하게_쿼리_수가_일정하다() {
            // given
            User someone = saveUser();
            saveRooms(3, someone);
            long queriesWith3Rooms = countQueries(() -> roomService.getRooms(null));

            saveRooms(27, someone);

            // when
            long queriesWith30Rooms = countQueries(() -> roomService.getRooms(null));

            // then
            assertThat(queriesWith30Rooms)
                .isEqualTo(queriesWith3Rooms)
                .isLessThanOrEqualTo(MAX_QUERIES_PER_ROOM_LIST);
        }
    }

    private long countQueries(Runnable action) {
        flushAndClear();            // 1차 캐시에 남은 엔티티 때문에 쿼리가 덜 세지지 않게 비운다
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /** 방 count 개 생성. 절반은 대표 이미지를 달고, 모두 방장 외 회원 1명과 viewer 를 참여시킨다. */
    private void saveRooms(int count, User viewer) {
        for (int i = 0; i < count; i++) {
            User host = saveUser();
            String imageUrl = i % 2 == 0 ? "https://cdn.example.com/rooms/" + sequence.get() + ".png" : null;
            Room room = saveRoom(host, imageUrl);
            saveRoomUser(saveUser(), room);
            if (i % 3 == 0) {
                saveRoomUser(viewer, room);
            }
        }
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "qc-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Room saveRoom(User host, String imageUrl) {
        ImageFile image = null;
        if (imageUrl != null) {
            image = ImageFile.from(imageUrl);
            entityManager.persist(image);
        }
        Room room = roomRepository.save(Room.of(
            host, image, "모임 " + sequence.incrementAndGet(), null, List.of(ActivityDay.MONDAY), LocalTime.of(7, 0)
        ));
        saveRoomUser(host, room);   // 실제 방 생성처럼 방장도 참여자로 등록
        return room;
    }

    private RoomUser saveRoomUser(User user, Room room) {
        return roomUserRepository.save(RoomUser.of(user, room));
    }

    private Map<Long, RoomSummaryResponse> byRoomId(RoomListResponse response) {
        return response.rooms().stream()
            .collect(Collectors.toMap(RoomSummaryResponse::roomId, Function.identity()));
    }
}
