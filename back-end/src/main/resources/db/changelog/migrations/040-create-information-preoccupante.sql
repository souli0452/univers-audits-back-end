--liquibase formatted sql
--changeset dev:040-create-information-preoccupante

CREATE TABLE information_preoccupante (
    id                UUID         PRIMARY KEY,
    objet             VARCHAR(500) NOT NULL,
    description       TEXT         NOT NULL,
    source            VARCHAR(40)  NOT NULL,
    source_reference  VARCHAR(500),
    date_reception    TIMESTAMP    NOT NULL,
    statut            VARCHAR(30)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE TABLE information_preoccupante_dossier (
    id                            UUID         PRIMARY KEY,
    information_preoccupante_id  UUID         NOT NULL REFERENCES information_preoccupante(id),
    dossier_id                   UUID         NOT NULL REFERENCES dossier(id),
    commentaire                  VARCHAR(2000),
    version                      BIGINT       NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP    NOT NULL,
    updated_at                   TIMESTAMP,
    created_by_id                VARCHAR(100),
    updated_by_id                VARCHAR(100)
);

CREATE UNIQUE INDEX idx_ip_dossier_unique
    ON information_preoccupante_dossier (information_preoccupante_id, dossier_id);

CREATE INDEX idx_ip_dossier_information
    ON information_preoccupante_dossier (information_preoccupante_id);

CREATE INDEX idx_ip_dossier_dossier
    ON information_preoccupante_dossier (dossier_id);

COMMENT ON TABLE information_preoccupante IS 'Signaux de veille (Lot 9 sous-chantier 1/3) - rattachables a posteriori a un ou plusieurs dossiers, ou pouvant declencher une auto-saisine';
COMMENT ON TABLE information_preoccupante_dossier IS 'Rattachement N-N information preoccupante / dossier, patron SeanceCtadpDossier';
