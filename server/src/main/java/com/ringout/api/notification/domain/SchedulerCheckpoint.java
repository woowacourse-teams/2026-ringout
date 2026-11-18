package com.ringout.api.notification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 스케줄러가 어디까지 처리했는지 기록한다. @Version 으로 두 인스턴스의 동시 처리를 막는다.
@Getter
@Entity
@Table(name = "scheduler_checkpoint")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerCheckpoint {

    @Id
    private String name;

    private LocalDateTime lastProcessedAt;

    @Version
    private Long version;

    private SchedulerCheckpoint(String name) {
        this.name = name;
    }

    public static SchedulerCheckpoint start(String name) {
        return new SchedulerCheckpoint(name);
    }

    // 처음 실행이면 now 부터, 아니면 마지막으로 처리한 분의 다음 분부터 처리한다.
    public LocalDateTime nextMinuteOr(LocalDateTime now) {
        return lastProcessedAt == null ? now : lastProcessedAt.plusMinutes(1);
    }

    public void advanceTo(LocalDateTime minute) {
        this.lastProcessedAt = minute;
    }
}
