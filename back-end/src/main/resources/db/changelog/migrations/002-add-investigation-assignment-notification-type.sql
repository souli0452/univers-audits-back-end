--liquibase formatted sql
--changeset dev:002-add-investigation-assignment-notification-type

ALTER TABLE notification
DROP CONSTRAINT IF EXISTS notification_type_check;

ALTER TABLE notification
ADD CONSTRAINT notification_type_check
CHECK (type IN (
    'RECEIPT_B4',
    'ACKNOWLEDGMENT_B5',
    'COMPLEMENT_REQUEST',
    'INADMISSIBILITY_DECISION',
    'TRANSFER_DECISION',
    'FINAL_DECISION',
    'DEADLINE_ALERT',
    'INTERNAL_ALERT',
    'STATUS_UPDATE',
    'INVESTIGATION_ALERT',
    'INVESTIGATION_ASSIGNMENT'
));