--liquibase formatted sql
--changeset dev:020-add-engagement-confidentialite

CREATE TABLE engagement_confidentialite (
    id                       UUID PRIMARY KEY,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP,
    created_by_id            VARCHAR(100),
    updated_by_id            VARCHAR(100),
    investigation_id         UUID NOT NULL REFERENCES investigation(id),
    agent_id                 UUID NOT NULL REFERENCES agent(id),
    has_conflict_of_interest BOOLEAN NOT NULL,
    conflict_details         VARCHAR(2000),
    signed_at                TIMESTAMP NOT NULL,
    CONSTRAINT uq_engagement_confidentialite_investigation_agent
        UNIQUE (investigation_id, agent_id)
);

CREATE INDEX idx_engagement_confidentialite_investigation
    ON engagement_confidentialite (investigation_id);

COMMENT ON TABLE engagement_confidentialite IS 'Declaration de conflit d interets + signature de l engagement de confidentialite par un agent, prealable a son affectation a une equipe d investigation (Lot 3, plan de travail S8.1/S8.4/S11) - une par couple (investigation, agent), jamais reemise';
