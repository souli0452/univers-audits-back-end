--liquibase formatted sql
--changeset dev:036-create-plan-actions

CREATE TABLE plan_actions (
    id                UUID         PRIMARY KEY,
    investigation_id  UUID         NOT NULL UNIQUE REFERENCES investigation(id),
    entite_controlee  VARCHAR(300) NOT NULL,
    contenu           TEXT         NOT NULL,
    submitted_at      TIMESTAMP    NOT NULL,
    received_by_id    UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE TABLE note_avancement (
    id              UUID      PRIMARY KEY,
    plan_actions_id UUID      NOT NULL REFERENCES plan_actions(id),
    note_at         TIMESTAMP NOT NULL,
    agent_id        UUID      NOT NULL REFERENCES agent(id),
    contenu         TEXT,
    version         BIGINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by_id   VARCHAR(100),
    updated_by_id   VARCHAR(100)
);

CREATE INDEX idx_note_avancement_plan_actions
    ON note_avancement (plan_actions_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'PLAN_ACTIONS_ENTITE_CONTROLEE', 'Délai de dépôt du plan d''actions par l''entité contrôlée après note de recommandations', 20, TRUE, TRUE, 0, now());

COMMENT ON TABLE plan_actions IS 'Depot du plan d actions par l entite controlee (Lot 6 sous-chantier 2/5) - evenement factuel, une seule par investigation, contrairement a TransmissionAutorite le delai de depot est suivi via planActionsOverdue meme avant depot';
COMMENT ON TABLE note_avancement IS 'Notes de suivi de l execution du plan d actions - rattachees a un plan_actions';
