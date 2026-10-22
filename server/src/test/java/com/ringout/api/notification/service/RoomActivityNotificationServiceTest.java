package com.ringout.api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.notification.domain.Campaign;
import com.ringout.api.notification.domain.CampaignTargetType;
import com.ringout.api.notification.domain.Device;
import com.ringout.api.notification.domain.Notification;
import com.ringout.api.notification.domain.NotificationStatus;
import com.ringout.api.notification.domain.Platform;
import com.ringout.api.notification.domain.TargetType;
import com.ringout.api.notification.repository.CampaignRepository;
import com.ringout.api.notification.repository.DeviceTokenRepository;
import com.ringout.api.notification.repository.NotificationRepository;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
@Import({JpaAuditingConfig.class, RoomActivityNotificationService.class})
class RoomActivityNotificationServiceTest {

    // 2026-10-19 은 월요일
    private static final LocalDateTime MONDAY_09_00 = LocalDateTime.of(2026, 10, 19, 9, 0);

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private RoomActivityNotificationService roomActivityNotificationService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomUserRepository roomUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void 활동_시각인_방의_멤버_디바이스마다_대기_상태_알림을_생성하고_id를_반환한다() {
        // given
        User host = saveUser();
        User member = saveUser();
        Room room = saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0));
        saveRoomUser(member, room);
        Device hostDevice = saveDevice(host);
        Device memberDevice = saveDevice(member);

        // when
        List<Long> ids = roomActivityNotificationService.create(MONDAY_09_00);

        // then
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(ids).containsExactlyInAnyOrderElementsOf(
            notifications.stream().map(Notification::getId).toList());
        assertThat(notifications)
            .extracting(notification -> notification.getDevice().getId())
            .containsExactlyInAnyOrder(hostDevice.getId(), memberDevice.getId());
        assertThat(notifications)
            .allSatisfy(notification -> {
                assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
                assertThat(notification.getTargetType()).isEqualTo(TargetType.ROOM);
            });
    }

    @Test
    void 방마다_방_이름을_제목으로_하는_캠페인을_생성한다() {
        // given
        User host = saveUser();
        Room room1 = saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0));
        Room room2 = saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0));
        saveDevice(host);

        // when
        roomActivityNotificationService.create(MONDAY_09_00);

        // then
        List<Campaign> campaigns = campaignRepository.findAll();
        assertThat(campaigns)
            .extracting(Campaign::getTitle)
            .containsExactlyInAnyOrder(room1.getName(), room2.getName());
        assertThat(campaigns)
            .extracting(Campaign::getCampaignTargetType)
            .containsOnly(CampaignTargetType.ROOM_MEMBER);
        assertThat(notificationRepository.findAll())
            .extracting(notification -> notification.getCampaign().getId())
            .containsExactlyInAnyOrderElementsOf(campaigns.stream().map(Campaign::getId).toList());
    }

    @Test
    void 캠페인에_방_id와_활동_날짜를_데이터로_담는다() {
        // given
        User host = saveUser();
        Room room = saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0));
        saveDevice(host);

        // when
        roomActivityNotificationService.create(MONDAY_09_00);

        // then
        assertThat(campaignRepository.findAll())
            .singleElement()
            .extracting(Campaign::getData)
            .isEqualTo(Map.of("roomId", room.getId().toString(), "activityDate", "2026-10-19"));
    }

    @Test
    void 활동_시각인_방이_없으면_아무것도_생성하지_않는다() {
        // given
        User host = saveUser();
        saveRoom(host, List.of(ActivityDay.TUESDAY), LocalTime.of(9, 0));
        saveDevice(host);

        // when
        List<Long> ids = roomActivityNotificationService.create(MONDAY_09_00);

        // then
        assertThat(ids).isEmpty();
        assertThat(campaignRepository.findAll()).isEmpty();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    void 멤버에게_디바이스가_없으면_캠페인을_생성하지_않는다() {
        // given
        User host = saveUser();
        saveRoom(host, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0));

        // when
        List<Long> ids = roomActivityNotificationService.create(MONDAY_09_00);

        // then
        assertThat(ids).isEmpty();
        assertThat(campaignRepository.findAll()).isEmpty();
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "ran-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Room saveRoom(User host, List<ActivityDay> activityDays, LocalTime activityTime) {
        Room room = roomRepository.save(Room.of(
            host, null, "모임 " + sequence.incrementAndGet(), null, activityDays, activityTime
        ));
        saveRoomUser(host, room);
        return room;
    }

    private RoomUser saveRoomUser(User user, Room room) {
        return roomUserRepository.save(RoomUser.of(user, room));
    }

    private Device saveDevice(User user) {
        return deviceTokenRepository.save(Device.of(user, "token-" + sequence.incrementAndGet(), Platform.IOS));
    }
}
