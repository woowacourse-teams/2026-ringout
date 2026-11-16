package com.ringout.api.notification.service;

import static com.ringout.api.notification.service.NotificationSendService.SENDING_LEASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.config.ClockConfig;
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
import com.ringout.api.notification.sender.SendResult;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
@Import({JpaAuditingConfig.class, ClockConfig.class, NotificationSendService.class})
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

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PushSender pushSender;

    @Test
    void 캠페인_문구와_데이터를_디바이스_토큰으로_푸시를_보낸다() {
        // given
        Campaign campaign = saveCampaign("아침 러닝 모임");
        Device device = saveDevice(saveUser());
        Notification notification = saveNotification(device, campaign);
        given(pushSender.send(anyList())).willReturn(List.of(SendResult.SENT));

        // when
        notificationSendService.send(List.of(notification.getId()));

        // then
        then(pushSender).should().send(List.of(
            new PushMessage(device.getFcmToken(), "아침 러닝 모임", campaign.getBody(),
                Map.of("roomId", "1", "activityDate", "2026-10-19"), "campaign-" + campaign.getId())
        ));
    }

    @Test
    void 발송_결과에_따라_성공은_SENT_실패는_FAILED로_변경한다() {
        // given
        Campaign campaign = saveCampaign("아침 러닝 모임");
        Notification success = saveNotification(saveDevice(saveUser()), campaign);
        Notification failure = saveNotification(saveDevice(saveUser()), campaign);
        given(pushSender.send(anyList())).willReturn(List.of(SendResult.SENT, SendResult.PERMANENT));

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

    @Test
    void 토큰이_없는_디바이스의_알림은_보내지_않고_FAILED로_변경한다() {
        // given
        Campaign campaign = saveCampaign("아침 러닝 모임");
        Device active = saveDevice(saveUser());
        Device cleared = saveDevice(saveUser());
        Notification deliverable = saveNotification(active, campaign);
        Notification tokenless = saveNotification(cleared, campaign);
        cleared.clearToken();
        deviceTokenRepository.flush();
        given(pushSender.send(anyList())).willReturn(List.of(SendResult.SENT));

        // when
        notificationSendService.send(List.of(deliverable.getId(), tokenless.getId()));

        // then
        then(pushSender).should().send(List.of(
            new PushMessage(active.getFcmToken(), "아침 러닝 모임", campaign.getBody(),
                Map.of("roomId", "1", "activityDate", "2026-10-19"), "campaign-" + campaign.getId())
        ));
        assertThat(notificationRepository.findById(deliverable.getId()).orElseThrow().getStatus())
            .isEqualTo(NotificationStatus.SENT);
        assertThat(notificationRepository.findById(tokenless.getId()).orElseThrow().getStatus())
            .isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void 모든_디바이스의_토큰이_없으면_푸시를_보내지_않는다() {
        // given
        Device cleared = saveDevice(saveUser());
        Notification tokenless = saveNotification(cleared, saveCampaign("아침 러닝 모임"));
        cleared.clearToken();
        deviceTokenRepository.flush();

        // when
        notificationSendService.send(List.of(tokenless.getId()));

        // then
        then(pushSender).should(never()).send(anyList());
        assertThat(notificationRepository.findById(tokenless.getId()).orElseThrow().getStatus())
            .isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void 이미_발송된_알림은_다시_보내지_않는다() {
        // given
        Notification notification = saveNotification(saveDevice(saveUser()), saveCampaign("아침 러닝 모임"));
        given(pushSender.send(anyList())).willReturn(List.of(SendResult.SENT));

        // when
        notificationSendService.send(List.of(notification.getId()));
        notificationSendService.send(List.of(notification.getId()));

        // then
        then(pushSender).should(times(1)).send(anyList());
    }

    @Test
    void 리스가_남은_SENDING_알림은_보내지_않는다() {
        // given
        Notification notification = saveNotification(saveDevice(saveUser()), saveCampaign("아침 러닝 모임"));
        claimByOtherSender(notification, "other-sender", LocalDateTime.now().minusMinutes(1));

        // when
        notificationSendService.send(List.of(notification.getId()));

        // then
        then(pushSender).should(never()).send(anyList());
        assertThat(statusOf(notification)).isEqualTo(NotificationStatus.SENDING);
    }

    @Test
    void 리스가_만료된_SENDING_알림은_다시_선점해_보낸다() {
        // given
        Notification notification = saveNotification(saveDevice(saveUser()), saveCampaign("아침 러닝 모임"));
        claimByOtherSender(notification, "stale-sender", LocalDateTime.now().minusMinutes(6));
        given(pushSender.send(anyList())).willReturn(List.of(SendResult.SENT));

        // when
        notificationSendService.send(List.of(notification.getId()));

        // then
        then(pushSender).should().send(anyList());
        assertThat(statusOf(notification)).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void 선점을_잃은_발송자는_상태를_덮어쓰지_않는다() {
        // given
        Notification notification = saveNotification(saveDevice(saveUser()), saveCampaign("아침 러닝 모임"));
        claimByOtherSender(notification, "stale-sender", LocalDateTime.now().minusMinutes(6));
        claimByOtherSender(notification, "new-sender", LocalDateTime.now());

        // when
        int updated = notificationRepository.updateStatus(
            List.of(notification.getId()), NotificationStatus.FAILED, "stale-sender");

        // then
        assertThat(updated).isZero();
        assertThat(statusOf(notification)).isEqualTo(NotificationStatus.SENDING);
    }

    @Test
    void 유예_시간이_지난_PENDING만_복구한다() {
        // given
        Campaign campaign = saveCampaign("아침 러닝 모임");
        Notification stuck = saveNotification(saveDevice(saveUser()), campaign);
        Notification fresh = saveNotification(saveDevice(saveUser()), campaign);
        changeCreatedAt(stuck, LocalDateTime.now().minusMinutes(2));
        given(pushSender.send(anyList())).willReturn(List.of(SendResult.SENT));

        // when
        notificationSendService.recover();

        // then
        then(pushSender).should(times(1)).send(anyList());
        assertThat(statusOf(stuck)).isEqualTo(NotificationStatus.SENT);
        assertThat(statusOf(fresh)).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void 발송_시한이_지난_알림은_보내지_않고_만료_처리한다() {
        // given
        Notification stale = saveNotification(saveDevice(saveUser()), saveCampaign("아침 러닝 모임"));
        changeCreatedAt(stale, LocalDateTime.now().minusHours(1));

        // when
        notificationSendService.recover();

        // then
        then(pushSender).should(never()).send(anyList());
        assertThat(statusOf(stale)).isEqualTo(NotificationStatus.EXPIRED);
    }

    private void claimByOtherSender(Notification notification, String claimToken, LocalDateTime claimedAt) {
        notificationRepository.claim(List.of(notification.getId()), claimToken, claimedAt,
            claimedAt.minus(SENDING_LEASE));
    }

    private void changeCreatedAt(Notification notification, LocalDateTime createdAt) {
        entityManager.createNativeQuery("update notification set created_at = :createdAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", notification.getId())
            .executeUpdate();
    }

    private NotificationStatus statusOf(Notification notification) {
        return notificationRepository.findById(notification.getId()).orElseThrow().getStatus();
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
            Campaign.of(title, "활동 시간이에요! 지금 참여해보세요.",
                Map.of("roomId", "1", "activityDate", "2026-10-19"), CampaignTargetType.ROOM_MEMBER));
    }

    private Notification saveNotification(Device device, Campaign campaign) {
        return notificationRepository.save(Notification.of(device.getUser(), device, campaign, TargetType.ROOM));
    }
}
