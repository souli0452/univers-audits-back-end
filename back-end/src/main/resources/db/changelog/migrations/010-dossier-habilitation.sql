--liquibase formatted sql
--changeset dev:010-dossier-habilitation

CREATE TABLE dossier_habilitation (
    id                  UUID PRIMARY KEY,
    dossier_id          UUID         NOT NULL REFERENCES dossier(id),
    agent_id            UUID         NOT NULL REFERENCES agent(id),
    source              VARCHAR(25)  NOT NULL,
    granted_by_id       UUID         REFERENCES agent(id),
    reason              VARCHAR(500),
    revoked_at          TIMESTAMP,
    revoked_by_id       UUID         REFERENCES agent(id),
    revocation_reason   VARCHAR(500),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by_id       VARCHAR(100),
    updated_by_id       VARCHAR(100)
);

CREATE INDEX idx_dossier_habilitation_dossier_agent
    ON dossier_habilitation (dossier_id, agent_id);
CREATE INDEX idx_dossier_habilitation_agent
    ON dossier_habilitation (agent_id);

-- Backfill obligatoire : sans ceci, tout agent perdrait l'accès à ses dossiers
-- actuels au déploiement (le nouveau garde-fou ne connaîtrait aucune habilitation).
INSERT INTO dossier_habilitation
    (id, dossier_id, agent_id, source, reason, version, created_at)
SELECT gen_random_uuid(), d.id, d.agent_in_charge_id, 'AGENT_IN_CHARGE',
       'Backfill migration 010 — agent en charge existant', 0, now()
FROM dossier d
WHERE d.agent_in_charge_id IS NOT NULL;

-- DISTINCT ON (dossier, agent) : un agent peut apparaître plusieurs fois comme
-- membre actif d'une même investigation (lignes historiques dupliquées).
-- Un simple "SELECT DISTINCT" ne suffirait pas ici car gen_random_uuid() rend
-- chaque ligne unique — DISTINCT ON déduplique explicitement sur la paire
-- (dossier, agent) avant de générer l'id. Sans ça, deux lignes actives
-- seraient créées pour la même (dossier, agent, source), et une révocation
-- ultérieure de l'une laisserait l'autre active silencieusement.
INSERT INTO dossier_habilitation
    (id, dossier_id, agent_id, source, reason, version, created_at)
SELECT DISTINCT ON (i.case_id, im.agent_id)
       gen_random_uuid(), i.case_id, im.agent_id, 'INVESTIGATION_TEAM',
       'Backfill migration 010 — membre d''équipe actif existant', 0, now()
FROM investigation_member im
JOIN investigation i ON i.id = im.investigation_id
WHERE im.active = TRUE
ORDER BY i.case_id, im.agent_id;

-- Garde-fou au niveau base : au plus une ligne active par (dossier, agent,
-- source). Deux sources différentes pour le même agent (ex : AGENT_IN_CHARGE
-- ET INVESTIGATION_TEAM) restent autorisées à coexister — c'est voulu.
-- Placé après les backfills ci-dessus (et leur DISTINCT) pour ne jamais être
-- bloqué par des doublons hérités des données existantes.
CREATE UNIQUE INDEX idx_dossier_habilitation_unique_active
    ON dossier_habilitation (dossier_id, agent_id, source)
    WHERE revoked_at IS NULL;
