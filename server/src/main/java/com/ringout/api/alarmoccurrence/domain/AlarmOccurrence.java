package com.ringout.api.alarmoccurrence.domain;

import com.ringout.api.alarmoccurrence.status.AlarmOccurrenceErrorStatus;
import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.domain.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "alarm_occurrence",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_alarm_occurrence_uuid", columnNames = "occurrence_uuid"),
        @UniqueConstraint(
            name = "uk_alarm_occurrence_user_alarm_scheduled",
            columnNames = {"user_id", "client_alarm_id", "scheduled_at"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmOccurrence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurrence_uuid", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String occurrenceUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "client_alarm_id", nullable = false, length = 64)
    private String clientAlarmId;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "alarm_time", nullable = false)
    private LocalTime alarmTime;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_type", length = 20)
    private OccurrenceEndType endType;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @OrderBy("ringingAt ASC, id ASC")
    @OneToMany(mappedBy = "alarmOccurrence", cascade = CascadeType.PERSIST)
    private List<AlarmRinging> ringings = new ArrayList<>();

    private AlarmOccurrence(
        String occurrenceUuid,
        User user,
        String clientAlarmId,
        LocalDateTime scheduledAt,
        LocalTime alarmTime,
        LocalDateTime startedAt
    ) {
        this.occurrenceUuid = occurrenceUuid;
        this.user = user;
        this.clientAlarmId = clientAlarmId;
        this.scheduledAt = scheduledAt;
        this.alarmTime = alarmTime;
        this.startedAt = startedAt;
        this.ringings.add(AlarmRinging.initial(this, startedAt));
    }

    public static AlarmOccurrence start(
        User user,
        String clientAlarmId,
        LocalDateTime scheduledAt,
        LocalTime alarmTime,
        LocalDateTime startedAt
    ) {
        return new AlarmOccurrence(UUID.randomUUID().toString(), user, clientAlarmId, scheduledAt, alarmTime,
            startedAt);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void ringRepeat(String eventId, LocalDateTime ringingAt) {
        if (findRepeatRinging(eventId).isPresent()) {
            return;
        }
        validateNotEnded();
        ringings.add(AlarmRinging.repeat(this, eventId, ringingAt));
    }

    public void dismiss(String eventId, LocalDateTime dismissedAt) {
        AlarmRinging ringing = eventId == null ? initialRinging() : findRepeatRinging(eventId)
            .orElseThrow(() -> new IllegalStateException("재울림이 기록되지 않았습니다. eventId=" + eventId));
        if (ringing.isDismissed()) {
            return;
        }
        validateNotEnded();
        ringing.dismiss(dismissedAt);
    }

    public void end(OccurrenceEndType endType, LocalDateTime endedAt) {
        if (this.endType == null) {
            this.endType = endType;
            this.endedAt = endedAt;
            return;
        }
        if (this.endType == endType && this.endedAt.equals(endedAt)) {
            return;
        }
        throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ALREADY_ENDED);
    }

    private AlarmRinging initialRinging() {
        return ringings.stream()
            .filter(AlarmRinging::isInitial)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("최초 울림이 없습니다. occurrenceUuid=" + occurrenceUuid));
    }

    private Optional<AlarmRinging> findRepeatRinging(String eventId) {
        return ringings.stream()
            .filter(ringing -> !ringing.isInitial() && ringing.hasEventId(eventId))
            .findFirst();
    }

    private void validateNotEnded() {
        if (endType != null) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ALREADY_ENDED);
        }
    }
}
