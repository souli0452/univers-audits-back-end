--liquibase formatted sql
--changeset dev:008-create-demande-documents

CREATE TABLE demande_documents (
    id                    UUID PRIMARY KEY,
    investigation_id      UUID         NOT NULL REFERENCES investigation(id),
    recipient_label       VARCHAR(300) NOT NULL,
    documents_requested   TEXT         NOT NULL,
    requested_by_id       UUID         NOT NULL REFERENCES agent(id),
    sent_at               TIMESTAMP    NOT NULL,
    deadline              TIMESTAMP    NOT NULL,
    escalation_level      VARCHAR(20)  NOT NULL DEFAULT 'INITIAL',
    received              BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at           TIMESTAMP,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP,
    created_by_id         VARCHAR(100),
    updated_by_id         VARCHAR(100)
);

CREATE INDEX idx_demande_documents_investigation ON demande_documents (investigation_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_INITIAL', 'Délai de réponse à une demande initiale de documents', NULL, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_RELANCE', 'Délai de réponse après relance', NULL, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_SOMMATION', 'Délai de réponse après sommation', NULL, TRUE, TRUE, 0, now());
