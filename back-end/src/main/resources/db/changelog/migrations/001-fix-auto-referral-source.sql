-- liquibase formatted sql

-- changeset dev:002-add-notification-read-at
ALTER TABLE notification ADD COLUMN read_at TIMESTAMP NULL;
CREATE INDEX idx_notification_read_at ON notification (read_at);
-- rollback DROP INDEX idx_notification_read_at; ALTER TABLE notification DROP COLUMN read_at;