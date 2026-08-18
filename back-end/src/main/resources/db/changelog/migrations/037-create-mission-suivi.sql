--liquibase formatted sql
--changeset dev:037-create-mission-suivi

CREATE TABLE mission_suivi (
    id                        UUID         PRIMARY KEY,
    investigation_id          UUID         NOT NULL REFERENCES investigation(id),
    mission_date              TIMESTAMP    NOT NULL,
    conducted_by_id           UUID         NOT NULL REFERENCES agent(id),
    objectifs                 TEXT         NOT NULL,
    synthese_recommandations  TEXT         NOT NULL,
    nouvelles_recommandations TEXT,
    submitted_at              TIMESTAMP    NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMP    NOT NULL,
    updated_at                TIMESTAMP,
    created_by_id             VARCHAR(100),
    updated_by_id             VARCHAR(100)
);

CREATE INDEX idx_mission_suivi_investigation
    ON mission_suivi (investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'MISSION_SUIVI_PLAN_ACTIONS', 'Délai pour mener une mission de suivi après le dépôt du plan d''actions', 365, FALSE, TRUE, 0, now());

COMMENT ON TABLE mission_suivi IS 'Missions de verification terrain de l execution des plans d actions (Lot 6 sous-chantier 3/5) - liste, plusieurs missions possibles par investigation, contrairement a PlanActions/TransmissionAutorite';
