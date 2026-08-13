--liquibase formatted sql
--changeset dev:031-create-rapport-enquete-note-recommandations

CREATE TABLE rapport_enquete (
    id                        UUID      PRIMARY KEY,
    investigation_id          UUID      NOT NULL UNIQUE REFERENCES investigation(id),
    titre                     TEXT,
    introduction              TEXT,
    methodologie              TEXT,
    informations_collectees   TEXT,
    expose_factuel_anomalies  TEXT,
    quantification_prejudice  TEXT,
    reserves                  TEXT,
    conclusions               TEXT,
    version                   BIGINT    NOT NULL DEFAULT 0,
    created_at                TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP,
    created_by_id             VARCHAR(100),
    updated_by_id             VARCHAR(100)
);

CREATE TABLE note_recommandations (
    id                 UUID      PRIMARY KEY,
    rapport_enquete_id UUID      NOT NULL UNIQUE REFERENCES rapport_enquete(id),
    contenu            TEXT,
    version            BIGINT    NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP,
    created_by_id      VARCHAR(100),
    updated_by_id      VARCHAR(100)
);

ALTER TABLE investigation DROP COLUMN final_report;
ALTER TABLE investigation DROP COLUMN conclusions;
ALTER TABLE investigation DROP COLUMN recommendations;

COMMENT ON TABLE rapport_enquete IS 'Rapport d enquete structure (Lot 5 sous-chantier 1/4) - remplace les anciens champs texte libres finalReport/conclusions/recommendations de investigation';
COMMENT ON COLUMN rapport_enquete.reserves IS 'Seul champ facultatif du rapport - les autres sont requis avant soumission (submitReport)';
COMMENT ON TABLE note_recommandations IS 'Note de recommandations, document distinct du rapport d enquete conformement au plan de travail ASCE-LC (Lot 5)';
