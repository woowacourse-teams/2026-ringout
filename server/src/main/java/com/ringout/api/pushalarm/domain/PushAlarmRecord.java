package com.ringout.api.pushalarm.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "push_alarm_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushAlarmRecord extends BaseEntity {

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
    @JoinColumn(name = "push_alarm_content_id", nullable = false)
    private PushAlarmContent pushAlarmContent;

    @Column(name = "push_alarm_status", nullable = false, length = 20)
    private String pushAlarmStatus;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private PushAlarmRecord(
        User user,
        Device device,
        PushAlarmContent pushAlarmContent,
        String pushAlarmStatus,
        boolean read
    ) {
        this.user = user;
        this.device = device;
        this.pushAlarmContent = pushAlarmContent;
        this.pushAlarmStatus = pushAlarmStatus;
        this.read = read;
    }

    public static PushAlarmRecord of(
        User user,
        Device device,
        PushAlarmContent pushAlarmContent,
        String pushAlarmStatus,
        boolean read
    ) {
        return new PushAlarmRecord(user, device, pushAlarmContent, pushAlarmStatus, read);
    }
}
