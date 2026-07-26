--liquibase formatted sql
--changeset dev:007-create-pv-audition

CREATE TABLE pv_audition (
    id                             UUID PRIMARY KEY,
    audition_id                    UUID         NOT NULL REFERENCES audition(id),
    content                        TEXT         NOT NULL,
    drafted_by_id                  UUID         NOT NULL REFERENCES agent(id),
    interviewee_signed             BOOLEAN      NOT NULL DEFAULT FALSE,
    interviewee_signature_refused  BOOLEAN      NOT NULL DEFAULT FALSE,
    finalized_at                   TIMESTAMP,
    version                        BIGINT       NOT NULL DEFAULT 0,
    created_at                     TIMESTAMP    NOT NULL,
    updated_at                     TIMESTAMP,
    created_by_id                  VARCHAR(100),
    updated_by_id                  VARCHAR(100)
);

CREATE UNIQUE INDEX idx_pv_audition_audition ON pv_audition (audition_id);
