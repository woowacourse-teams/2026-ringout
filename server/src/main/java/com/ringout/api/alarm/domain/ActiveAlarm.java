package com.ringout.api.alarm.domain;

import com.ringout.api.common.BaseEntity;
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
@Table(name = "active_alarm")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActiveAlarm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_id", nullable = false)
    private Alarm alarm;

    @Column(name = "active_datetime", nullable = false)
    private LocalDateTime activeDatetime;

    private ActiveAlarm(Alarm alarm, LocalDateTime activeDatetime) {
        this.alarm = alarm;
        this.activeDatetime = activeDatetime;
    }

    public static ActiveAlarm of(Alarm alarm, LocalDateTime activeDatetime) {
        return new ActiveAlarm(alarm, activeDatetime);
    }
}
