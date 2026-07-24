--liquibase formatted sql
--changeset dev:003-add-version-to-role-portal-config

ALTER TABLE role_definition
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE portal_config
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
