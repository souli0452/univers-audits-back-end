--liquibase formatted sql
--changeset dev:006-create-audition

CREATE TABLE audition (
    id                   UUID PRIMARY KEY,
    investigation_id     UUID         NOT NULL REFERENCES investigation(id),
    interviewee_type     VARCHAR(20)  NOT NULL,
    targeted_party_id    UUID REFERENCES targeted_party(id),
    witness_id           UUID REFERENCES witness(id),
    conducted_by_id      UUID         NOT NULL REFERENCES agent(id),
    location             VARCHAR(300),
    scheduled_at         TIMESTAMP    NOT NULL,
    conducted_at         TIMESTAMP,
    status               VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    summary              TEXT,
    cancellation_reason  TEXT,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,
    created_by_id        VARCHAR(100),
    updated_by_id        VARCHAR(100)
);

CREATE INDEX idx_audition_investigation ON audition (investigation_id);
