ALTER TABLE room ADD COLUMN latest_activity_at DATETIME NULL;

UPDATE room SET latest_activity_at = updated_at;

ALTER TABLE room MODIFY COLUMN latest_activity_at DATETIME NOT NULL;

CREATE INDEX idx_room_deleted_latest_activity_at ON room (deleted_at, latest_activity_at DESC, id ASC);
