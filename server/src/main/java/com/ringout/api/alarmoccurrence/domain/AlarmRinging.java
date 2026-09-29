package com.ringout.api.alarmoccurrence.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "alarm_ringing",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_alarm_ringing_occurrence_event",
        columnNames = {"alarm_occurrence_id", "event_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmRinging extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_occurrence_id", nullable = false)
    private AlarmOccurrence alarmOccurrence;

    @Enumerated(EnumType.STRING)
    @Column(name = "ringing_type", nullable = false, length = 20)
    private RingingType type;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "ringing_at", nullable = false)
    private LocalDateTime ringingAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    private AlarmRinging(AlarmOccurrence alarmOccurrence, RingingType type, String eventId, LocalDateTime ringingAt) {
        this.alarmOccurrence = alarmOccurrence;
        this.type = type;
        this.eventId = eventId;
        this.ringingAt = ringingAt;
    }

    static AlarmRinging initial(AlarmOccurrence alarmOccurrence, LocalDateTime ringingAt) {
        return new AlarmRinging(alarmOccurrence, RingingType.INITIAL, null, ringingAt);
    }

    static AlarmRinging repeat(AlarmOccurrence alarmOccurrence, String eventId, LocalDateTime ringingAt) {
        return new AlarmRinging(alarmOccurrence, RingingType.REPEAT, eventId, ringingAt);
    }

    boolean isInitial() {
        return type == RingingType.INITIAL;
    }

    boolean hasEventId(String eventId) {
        return Objects.equals(this.eventId, eventId);
    }

    boolean isDismissed() {
        return dismissedAt != null;
    }

    void dismiss(LocalDateTime dismissedAt) {
        this.dismissedAt = dismissedAt;
    }
}
