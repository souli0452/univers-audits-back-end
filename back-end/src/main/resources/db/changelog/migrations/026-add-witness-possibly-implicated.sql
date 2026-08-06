--liquibase formatted sql
--changeset dev:026-add-witness-possibly-implicated

ALTER TABLE witness ADD COLUMN possibly_implicated BOOLEAN NOT NULL DEFAULT false;
