-- liquibase formatted sql

-- changeset dev:001-fix-auto-referral-source
ALTER TABLE dossier ALTER COLUMN auto_referral_source TYPE VARCHAR(30);
-- rollback ALTER TABLE dossier ALTER COLUMN auto_referral_source TYPE SMALLINT;
