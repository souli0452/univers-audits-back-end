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

INSERT INTO dossier_habilitation
    (id, dossier_id, agent_id, source, reason, version, created_at)
SELECT gen_random_uuid(), i.case_id, im.agent_id, 'INVESTIGATION_TEAM',
       'Backfill migration 010 — membre d''équipe actif existant', 0, now()
FROM investigation_member im
JOIN investigation i ON i.id = im.investigation_id
WHERE im.active = TRUE;
