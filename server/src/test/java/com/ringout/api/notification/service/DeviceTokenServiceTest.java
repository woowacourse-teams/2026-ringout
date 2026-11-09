package com.ringout.api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.JpaAuditingConfig;
import com.ringout.api.notification.domain.Device;
import com.ringout.api.notification.domain.Platform;
import com.ringout.api.notification.dto.request.DeviceTokenDeleteRequest;
import com.ringout.api.notification.dto.request.DeviceTokenRegisterRequest;
import com.ringout.api.notification.repository.DeviceTokenRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
@Import({JpaAuditingConfig.class, DeviceTokenService.class})
class DeviceTokenServiceTest {

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 디바이스가_없으면_새로_생성한다() {
        // given
        User user = saveUser();

        // when
        deviceTokenService.register(user.getId(), registerRequest("token-a", Platform.IOS));
        flushAndClear();

        // then
        assertThat(deviceTokenRepository.findByUserId(user.getId()))
            .get()
            .extracting(Device::getFcmToken, Device::getPlatform)
            .containsExactly("token-a", Platform.IOS);
    }

    @Test
    void 디바이스가_있으면_같은_row에_토큰을_덮어쓴다() {
        // given
        User user = saveUser();
        Device device = deviceTokenRepository.save(Device.of(user, "old-token", Platform.IOS));

        // when
        deviceTokenService.register(user.getId(), registerRequest("new-token", Platform.ANDROID));
        flushAndClear();

        // then
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
        assertThat(deviceTokenRepository.findById(device.getId()))
            .get()
            .extracting(Device::getFcmToken, Device::getPlatform)
            .containsExactly("new-token", Platform.ANDROID);
    }

    @Test
    void 다른_유저가_쓰던_토큰이면_이전_유저의_토큰을_비우고_내_디바이스로_저장한다() {
        // given
        User previousUser = saveUser();
        User user = saveUser();
        Device previousDevice = deviceTokenRepository.save(Device.of(previousUser, "shared-token", Platform.IOS));

        // when
        deviceTokenService.register(user.getId(), registerRequest("shared-token", Platform.IOS));
        flushAndClear();

        // then
        assertThat(deviceTokenRepository.findById(previousDevice.getId()))
            .get()
            .extracting(Device::getFcmToken)
            .isNull();
        assertThat(deviceTokenRepository.findByUserId(user.getId()))
            .get()
            .extracting(Device::getFcmToken)
            .isEqualTo("shared-token");
    }

    @Test
    void 로그아웃하면_토큰만_비우고_디바이스는_남긴다() {
        // given
        User user = saveUser();
        Device device = deviceTokenRepository.save(Device.of(user, "token-a", Platform.IOS));

        // when
        deviceTokenService.unregister(user.getId(), new DeviceTokenDeleteRequest("token-a"));
        flushAndClear();

        // then
        assertThat(deviceTokenRepository.findById(device.getId()))
            .get()
            .extracting(Device::getFcmToken)
            .isNull();
    }

    @Test
    void 다른_유저의_토큰으로는_로그아웃되지_않는다() {
        // given
        User owner = saveUser();
        User other = saveUser();
        Device device = deviceTokenRepository.save(Device.of(owner, "token-a", Platform.IOS));

        // when
        deviceTokenService.unregister(other.getId(), new DeviceTokenDeleteRequest("token-a"));
        flushAndClear();

        // then
        assertThat(deviceTokenRepository.findById(device.getId()))
            .get()
            .extracting(Device::getFcmToken)
            .isEqualTo("token-a");
    }

    private DeviceTokenRegisterRequest registerRequest(String fcmToken, Platform platform) {
        return new DeviceTokenRegisterRequest(platform, fcmToken);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "dts-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }
}
