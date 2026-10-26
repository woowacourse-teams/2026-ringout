package com.ringout.api.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.notification.domain.Device;
import com.ringout.api.notification.domain.Platform;
import com.ringout.api.notification.repository.dto.RoomMemberDevice;
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
class DeviceTokenRepositoryTest {

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomUserRepository roomUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 방에_남아있는_멤버의_디바이스만_방_id와_함께_조회한다() {
        // given
        User host = saveUser();
        User member = saveUser();
        User leftMember = saveUser();
        User noDeviceMember = saveUser();

        Room room1 = saveRoom(host);
        Room room2 = saveRoom(noDeviceMember);
        saveRoomUser(member, room1);
        saveRoomUser(leftMember, room1).softDelete();
        roomUserRepository.flush();

        Device hostIos = saveDevice(host, Platform.IOS);
        Device hostAndroid = saveDevice(host, Platform.ANDROID);
        Device memberDevice = saveDevice(member, Platform.ANDROID);
        saveDevice(leftMember, Platform.IOS);

        // when
        List<RoomMemberDevice> result = deviceTokenRepository.findActiveRoomMemberDevices(
            List.of(room1.getId(), room2.getId()));

        // then
        assertThat(result)
            .extracting(RoomMemberDevice::roomId, roomMemberDevice -> roomMemberDevice.device().getId())
            .containsExactlyInAnyOrder(
                tuple(room1.getId(), hostIos.getId()),
                tuple(room1.getId(), hostAndroid.getId()),
                tuple(room1.getId(), memberDevice.getId())
            );
    }

    @Test
    void 여러_방에_속한_멤버의_디바이스는_방마다_조회한다() {
        // given
        User user = saveUser();
        Room room1 = saveRoom(user);
        Room room2 = saveRoom(user);
        Device device = saveDevice(user, Platform.IOS);

        // when
        List<RoomMemberDevice> result = deviceTokenRepository.findActiveRoomMemberDevices(
            List.of(room1.getId(), room2.getId()));

        // then
        assertThat(result)
            .extracting(RoomMemberDevice::roomId, roomMemberDevice -> roomMemberDevice.device().getId())
            .containsExactlyInAnyOrder(
                tuple(room1.getId(), device.getId()),
                tuple(room2.getId(), device.getId())
            );
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "dt-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Room saveRoom(User host) {
        Room room = roomRepository.save(Room.of(
            host, null, "모임 " + sequence.incrementAndGet(), null, List.of(ActivityDay.MONDAY), LocalTime.of(9, 0)
        ));
        saveRoomUser(host, room);
        return room;
    }

    private RoomUser saveRoomUser(User user, Room room) {
        return roomUserRepository.save(RoomUser.of(user, room));
    }

    private Device saveDevice(User user, Platform platform) {
        return deviceTokenRepository.save(Device.of(user, "token-" + sequence.incrementAndGet(), platform));
    }
}
