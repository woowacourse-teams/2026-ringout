package com.ringout.api.alarmmovement.domain;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "alarm_movement",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_alarm_movement_alarm_occurrence",
        columnNames = "alarm_occurrence_id"
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_occurrence_id", nullable = false)
    private AlarmOccurrence alarmOccurrence;

    @Column(name = "movement_started_at")
    private LocalDateTime movementStartedAt;

    @Column(name = "gave_up_at")
    private LocalDateTime gaveUpAt;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    private AlarmMovement(AlarmOccurrence alarmOccurrence, LocalDateTime movementStartedAt, LocalDateTime gaveUpAt,
        LocalDateTime arrivedAt) {
        validateTerminalActionTimes(gaveUpAt, arrivedAt);
        this.alarmOccurrence = alarmOccurrence;
        this.movementStartedAt = movementStartedAt;
        this.gaveUpAt = gaveUpAt;
        this.arrivedAt = arrivedAt;
    }

    public static AlarmMovement of(AlarmOccurrence alarmOccurrence, LocalDateTime movementStartedAt,
        LocalDateTime gaveUpAt,
        LocalDateTime arrivedAt) {
        return new AlarmMovement(alarmOccurrence, movementStartedAt, gaveUpAt, arrivedAt);
    }

    public MovementStatus change(MovementAction action, LocalDateTime actionAt) {
        validateAction(action);
        validateNotTerminated();

        return switch (action) {
            case START_MOVEMENT -> startMovement(actionAt);
            case GIVE_UP -> giveUp(actionAt);
            case ARRIVE -> arrive(actionAt);
        };
    }

    public MovementStatus getMovementStatus(LocalDateTime referenceTime) {
        if (gaveUpAt != null) {
            return MovementStatus.GAVE_UP;
        }
        if (arrivedAt != null) {
            return MovementStatus.ARRIVED;
        }
        if (movementStartedAt == null) {
            return MovementStatus.ALARM_TRIGGERED;
        }
        if (!movementStartedAt.plusMinutes(2).isAfter(referenceTime)) {
            return MovementStatus.MOVING;
        }
        return MovementStatus.MOVEMENT_STARTED;
    }

    private MovementStatus startMovement(LocalDateTime actionAt) {
        if (movementStartedAt != null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ALREADY_STARTED);
        }
        movementStartedAt = actionAt;
        return MovementStatus.MOVEMENT_STARTED;
    }

    private MovementStatus giveUp(LocalDateTime actionAt) {
        gaveUpAt = actionAt;
        return MovementStatus.GAVE_UP;
    }

    private MovementStatus arrive(LocalDateTime actionAt) {
        arrivedAt = actionAt;
        return MovementStatus.ARRIVED;
    }

    private void validateNotTerminated() {
        if (gaveUpAt != null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ALREADY_GAVE_UP);
        }
        if (arrivedAt != null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ALREADY_ARRIVED);
        }
    }

    private static void validateTerminalActionTimes(LocalDateTime gaveUpAt, LocalDateTime arrivedAt) {
        if (gaveUpAt != null && arrivedAt != null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_TERMINAL_STATE_CONFLICT);
        }
    }

    private static void validateAction(MovementAction action) {
        if (action == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ACTION_REQUIRED);
        }
    }
}
