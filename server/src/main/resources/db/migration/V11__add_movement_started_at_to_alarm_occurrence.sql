ALTER TABLE alarm_occurrence
    ADD COLUMN movement_started_at DATETIME NULL AFTER started_at;

UPDATE alarm_occurrence alarmOccurrence
JOIN alarm_movement alarmMovement ON alarmMovement.alarm_occurrence_id = alarmOccurrence.id
SET alarmOccurrence.movement_started_at = alarmMovement.movement_started_at,
    alarmOccurrence.end_type = CASE
        WHEN alarmOccurrence.end_type IS NULL AND alarmMovement.arrived_at IS NOT NULL THEN 'ARRIVED'
        WHEN alarmOccurrence.end_type IS NULL AND alarmMovement.gave_up_at IS NOT NULL THEN 'FORCE_ENDED'
        ELSE alarmOccurrence.end_type
    END,
    alarmOccurrence.ended_at = CASE
        WHEN alarmOccurrence.ended_at IS NULL AND alarmMovement.arrived_at IS NOT NULL THEN alarmMovement.arrived_at
        WHEN alarmOccurrence.ended_at IS NULL AND alarmMovement.gave_up_at IS NOT NULL THEN alarmMovement.gave_up_at
        ELSE alarmOccurrence.ended_at
    END
WHERE alarmMovement.deleted_at IS NULL;
