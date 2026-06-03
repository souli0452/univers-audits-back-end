ALTER TABLE notification ADD COLUMN IF NOT EXISTS read_at TIMESTAMP NULL;
CREATE INDEX IF NOT EXISTS idx_notification_read_at ON notification (read_at);