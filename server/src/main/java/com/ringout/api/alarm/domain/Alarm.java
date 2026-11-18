package com.ringout.api.alarm.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "alarm",
    uniqueConstraints = @UniqueConstraint(name = "uk_alarm_user_alarm_id", columnNames = {"user_id", "alarm_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alarm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Embedded
    private ClientAlarmId clientAlarmId;

    @Embedded
    private AlarmSetting setting;

    @Embedded
    private AlarmVersion version;

    private Alarm(User user, ClientAlarmId clientAlarmId, AlarmSetting setting, AlarmVersion version) {
        this.user = user;
        this.clientAlarmId = clientAlarmId;
        this.setting = setting;
        this.version = version;
    }

    public static Alarm create(User user, String alarmId, AlarmSetting setting, Long version) {
        return new Alarm(user, ClientAlarmId.from(alarmId), setting, AlarmVersion.from(version));
    }

    public String alarmId() {
        return clientAlarmId.getValue();
    }

    public LocalTime alarmTime() {
        return setting.alarmTime();
    }

    public List<AlarmDay> days() {
        return setting.days();
    }

    public int repeatMinutes() {
        return setting.repeatMinutes();
    }

    public String destinationName() {
        return setting.destinationName();
    }

    public double latitude() {
        return setting.latitude();
    }

    public double longitude() {
        return setting.longitude();
    }

    public long version() {
        return version.getValue();
    }
}
