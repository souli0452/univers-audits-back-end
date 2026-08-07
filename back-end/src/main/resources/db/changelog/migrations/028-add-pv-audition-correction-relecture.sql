--liquibase formatted sql
--changeset dev:028-add-pv-audition-correction-relecture

ALTER TABLE pv_audition ADD COLUMN pv_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pv_audition ADD COLUMN read_back_at TIMESTAMP;

CREATE TABLE correction_pv_audition (
    id                 UUID PRIMARY KEY,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP,
    created_by_id       VARCHAR(100),
    updated_by_id       VARCHAR(100),
    pv_audition_id      UUID NOT NULL REFERENCES pv_audition(id),
    version_number      INTEGER NOT NULL,
    content             TEXT NOT NULL,
    corrected_at        TIMESTAMP NOT NULL,
    corrected_by_id     UUID NOT NULL REFERENCES agent(id),
    motif_correction    TEXT NOT NULL
);

CREATE INDEX idx_correction_pv_audition_pv
    ON correction_pv_audition (pv_audition_id);

COMMENT ON COLUMN pv_audition.pv_version IS 'Compteur de version metier du contenu du PV, incremente a chaque correction post-finalisation';
COMMENT ON COLUMN pv_audition.read_back_at IS 'Horodatage de la relecture du PV a la personne auditionnee, prealable obligatoire a la signature';
COMMENT ON TABLE correction_pv_audition IS 'Historique des corrections post-finalisation d un PV d audition - snapshot immuable du contenu remplace a chaque correction (Lot 4, plan de travail S4)';
