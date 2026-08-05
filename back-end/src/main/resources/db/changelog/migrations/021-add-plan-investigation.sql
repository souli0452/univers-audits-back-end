--liquibase formatted sql
--changeset dev:021-add-plan-investigation

CREATE TABLE plan_investigation (
    id                   UUID PRIMARY KEY,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP,
    created_by_id        VARCHAR(100),
    updated_by_id        VARCHAR(100),
    investigation_id     UUID NOT NULL UNIQUE REFERENCES investigation(id),
    objectifs            TEXT NOT NULL,
    methodologie         TEXT NOT NULL,
    moyens_mobilises     TEXT,
    planning_procedures  TEXT,
    plan_version         INTEGER NOT NULL,
    submitted_at         TIMESTAMP NOT NULL,
    submitted_by_id      UUID NOT NULL REFERENCES agent(id),
    validated_at         TIMESTAMP,
    validated_by_id      UUID REFERENCES agent(id)
);

CREATE TABLE revision_plan (
    id                     UUID PRIMARY KEY,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP,
    created_by_id          VARCHAR(100),
    updated_by_id          VARCHAR(100),
    plan_investigation_id  UUID NOT NULL REFERENCES plan_investigation(id),
    version_number         INTEGER NOT NULL,
    objectifs              TEXT NOT NULL,
    methodologie           TEXT NOT NULL,
    moyens_mobilises       TEXT,
    planning_procedures    TEXT,
    revised_at             TIMESTAMP NOT NULL,
    revised_by_id          UUID NOT NULL REFERENCES agent(id),
    motif_revision         TEXT NOT NULL
);

CREATE INDEX idx_revision_plan_plan_investigation
    ON revision_plan (plan_investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'VALIDATION_PLAN_INVESTIGATION_DEI', 'Délai de validation du plan d''investigation par le DEI, après délivrance du mandat', 8, TRUE, TRUE, 0, now());

COMMENT ON TABLE plan_investigation IS 'Plan d investigation courant (mutable) d une investigation - objectifs/methodologie/moyens/planning, valide par le DEI (Lot 3, plan de travail S5/S7/S11)';
COMMENT ON TABLE revision_plan IS 'Historique complet des revisions du plan d investigation - snapshot immuable du contenu remplace a chaque revision (Lot 3, plan de travail S5)';
