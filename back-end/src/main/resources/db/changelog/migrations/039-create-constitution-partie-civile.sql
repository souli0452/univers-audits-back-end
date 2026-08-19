--liquibase formatted sql
--changeset dev:039-create-constitution-partie-civile

CREATE TABLE constitution_partie_civile (
    id                UUID           PRIMARY KEY,
    investigation_id  UUID           NOT NULL UNIQUE REFERENCES investigation(id),
    constitue_at      TIMESTAMP      NOT NULL,
    montant_reclame   NUMERIC(15,2),
    justification     TEXT           NOT NULL,
    constituee_par_id UUID           NOT NULL REFERENCES agent(id),
    submitted_at      TIMESTAMP      NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE constitution_partie_civile IS 'Acte de constitution de partie civile au nom de l Etat (Lot 6 sous-chantier 5/5, dernier du Lot 6) - evenement factuel unique par investigation, art. 58 loi organique 082-2015';
