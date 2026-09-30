DELETE FROM alarm_movement;

ALTER TABLE alarm_movement
    DROP FOREIGN KEY fk_alarm_movement_active_alarm,
    DROP INDEX uk_alarm_movement_active_alarm,
    CHANGE COLUMN active_alarm_id alarm_occurrence_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_alarm_movement_alarm_occurrence UNIQUE (alarm_occurrence_id),
    ADD CONSTRAINT fk_alarm_movement_alarm_occurrence
        FOREIGN KEY (alarm_occurrence_id) REFERENCES alarm_occurrence (id);

DROP TABLE IF EXISTS `record`;
DROP TABLE IF EXISTS active_alarm;
DROP TABLE IF EXISTS alarm;
