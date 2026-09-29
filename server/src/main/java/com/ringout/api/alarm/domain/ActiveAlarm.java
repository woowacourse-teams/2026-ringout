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
        // TODO: 활성 알람을 저장하는 애플리케이션 서비스는 같은 트랜잭션에서 AlarmMovement를 반드시 생성해야 한다.
        // ActiveAlarm만 존재하고 AlarmMovement가 없는 상태는 데이터 정합성 위반이다.
        return new ActiveAlarm(alarm, activeDatetime);
    }
}
