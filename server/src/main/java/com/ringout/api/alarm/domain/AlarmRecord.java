package com.ringout.api.alarm.domain;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_id", nullable = false)
    private Alarm alarm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_alarm_id")
    private ActiveAlarm activeAlarm;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;

    @Column(name = "user_action", length = 20)
    private String userAction;

    private AlarmRecord(
        User user,
        Alarm alarm,
        ActiveAlarm activeAlarm,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String userAction
    ) {
        this.user = user;
        this.alarm = alarm;
        this.activeAlarm = activeAlarm;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.userAction = userAction;
    }

    public static AlarmRecord of(
        User user,
        Alarm alarm,
        ActiveAlarm activeAlarm,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String userAction
    ) {
        return new AlarmRecord(user, alarm, activeAlarm, startDatetime, endDatetime, userAction);
    }
}
