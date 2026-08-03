--liquibase formatted sql
--changeset dev:018-add-decision-cge

CREATE TABLE decision_cge (
    id             UUID PRIMARY KEY,
    version        BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP,
    created_by_id  VARCHAR(100),
    updated_by_id  VARCHAR(100),
    dossier_id     UUID NOT NULL UNIQUE REFERENCES dossier(id),
    decision       VARCHAR(40) NOT NULL,
    motif          VARCHAR(2000),
    date_decision  TIMESTAMP NOT NULL,
    agent_cge_id   UUID NOT NULL REFERENCES agent(id)
);

COMMENT ON TABLE decision_cge IS 'Decision formelle du CGE sur un dossier (Lot 2, plan de travail S6/S11) - une par dossier, reutilise l enum RecommandationCtadp du sous-chantier SeanceCTADP';
