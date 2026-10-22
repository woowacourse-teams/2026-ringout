package com.ringout.api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import com.ringout.api.notification.sender.PushMessage;
import com.ringout.api.notification.sender.PushSender;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = {
    "spring.test.database.replace=none",
    "spring.datasource.url=jdbc:tc:mysql:8.4:///ringout",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({JpaAuditingConfig.class, NotificationSendService.class})
class NotificationSendServiceTest {

    private final AtomicLong sequence = new AtomicLong();

    @Autowired
    private NotificationSendService notificationSendService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PushSender pushSender;

    @Test
    void 캠페인_문구와_디바이스_토큰으로_푸시를_보낸다() {
        // given
        Campaign campaign = saveCampaign("아침 러닝 모임");
        Device device = saveDevice(saveUser());
        Notification notification = saveNotification(device, campaign);
        given(pushSender.send(anyList())).willReturn(List.of(true));

        // when
        notificationSendService.send(List.of(notification.getId()));

        // then
        then(pushSender).should().send(List.of(
            new PushMessage(device.getFcmToken(), "아침 러닝 모임", campaign.getBody(), null)
        ));
    }

    @Test
    void 발송_결과에_따라_성공은_SENT_실패는_FAILED로_변경한다() {
        // given
        Campaign campaign = saveCampaign("아침 러닝 모임");
        Notification success = saveNotification(saveDevice(saveUser()), campaign);
        Notification failure = saveNotification(saveDevice(saveUser()), campaign);
        given(pushSender.send(anyList())).willReturn(List.of(true, false));

        // when
        notificationSendService.send(List.of(success.getId(), failure.getId()));

        // then
        assertThat(notificationRepository.findById(success.getId()).orElseThrow().getStatus())
            .isEqualTo(NotificationStatus.SENT);
        assertThat(notificationRepository.findById(failure.getId()).orElseThrow().getStatus())
            .isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void 보낼_알림이_없으면_푸시를_보내지_않는다() {
        // when
        notificationSendService.send(List.of());

        // then
        then(pushSender).should(never()).send(anyList());
    }

    private User saveUser() {
        return userRepository.save(
            User.register(SocialProvider.KAKAO, "ns-" + sequence.incrementAndGet(), null, LocalDateTime.now())
        );
    }

    private Device saveDevice(User user) {
        return deviceTokenRepository.save(Device.of(user, "token-" + sequence.incrementAndGet(), Platform.IOS));
    }

    private Campaign saveCampaign(String title) {
        return campaignRepository.save(
            Campaign.of(title, "활동 시간이에요! 지금 참여해보세요.", null, CampaignTargetType.ROOM_MEMBER));
    }

    private Notification saveNotification(Device device, Campaign campaign) {
        return notificationRepository.save(Notification.of(device.getUser(), device, campaign, TargetType.ROOM));
    }
}
