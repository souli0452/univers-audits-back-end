--liquibase formatted sql
--changeset dev:038-create-suivi-procedure-penale

CREATE TABLE suivi_procedure_penale (
    id                UUID         PRIMARY KEY,
    investigation_id  UUID         NOT NULL REFERENCES investigation(id),
    phase_at          TIMESTAMP    NOT NULL,
    phase             VARCHAR(300) NOT NULL,
    commentaire       TEXT,
    agent_id          UUID         NOT NULL REFERENCES agent(id),
    submitted_at      TIMESTAMP    NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE INDEX idx_suivi_procedure_penale_investigation
    ON suivi_procedure_penale (investigation_id);

COMMENT ON TABLE suivi_procedure_penale IS 'Suivi chronologique des phases de la procedure penale (Lot 6 sous-chantier 4/5) - liste, plusieurs entrees possibles par investigation, pas de gate sur RequeteParquet (un dossier peut suivre une voie judiciaire sans requete au Parquet redigee dans ce systeme)';
