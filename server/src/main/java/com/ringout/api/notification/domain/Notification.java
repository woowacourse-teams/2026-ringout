package com.ringout.api.notification.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private boolean isRead;

    private LocalDateTime readAt;

    public void read() {
        if (this.isRead) {
            return;
        }
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public static Notification of(User user, Device device, Campaign campaign,
        TargetType targetType) {
        return Notification.builder()
            .user(user)
            .device(device)
            .campaign(campaign)
            .targetType(targetType)
            .build();
    }

    @Builder(access = AccessLevel.PRIVATE)
    public Notification(User user, Device device, Campaign campaign, TargetType targetType,
        LocalDateTime readAt) {
        this.user = user;
        this.device = device;
        this.campaign = campaign;
        this.isRead = false;
        this.targetType = targetType;
        this.status = NotificationStatus.PENDING;
    }
}
