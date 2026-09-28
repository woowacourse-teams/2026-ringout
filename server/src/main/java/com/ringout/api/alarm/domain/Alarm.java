package com.ringout.api.alarm.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.file.domain.Mp3File;
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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "alarm")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alarm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Mp3File song;

    @Column(name = "alarm_time", nullable = false)
    private LocalTime alarmTime;

    @Column(name = "interval_minutes", nullable = false)
    private Long intervalMinutes;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    private Alarm(
        User user,
        Destination destination,
        Mp3File song,
        LocalTime alarmTime,
        Long intervalMinutes,
        Integer dayOfWeek,
        boolean active
    ) {
        this.user = user;
        this.destination = destination;
        this.song = song;
        this.alarmTime = alarmTime;
        this.intervalMinutes = intervalMinutes;
        this.dayOfWeek = dayOfWeek;
        this.active = active;
    }

    public static Alarm of(
        User user,
        Destination destination,
        Mp3File song,
        LocalTime alarmTime,
        Long intervalMinutes,
        Integer dayOfWeek,
        boolean active
    ) {
        return new Alarm(user, destination, song, alarmTime, intervalMinutes, dayOfWeek, active);
    }
}
