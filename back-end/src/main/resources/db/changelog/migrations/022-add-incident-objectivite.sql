--liquibase formatted sql
--changeset dev:022-add-incident-objectivite

CREATE TABLE incident_objectivite (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL REFERENCES investigation(id),
    declared_by_id    UUID NOT NULL REFERENCES agent(id),
    description       TEXT NOT NULL,
    declared_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_incident_objectivite_investigation
    ON incident_objectivite (investigation_id);

COMMENT ON TABLE incident_objectivite IS 'Declaration d incident d objectivite par un agent affecte au dossier - tracee, permanente, distincte de la declaration de conflit d interets prealable (Lot 3, plan de travail S8.4/S11)';
