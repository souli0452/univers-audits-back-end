--liquibase formatted sql
--changeset dev:035-create-transmission-autorite

CREATE TABLE transmission_autorite (
    id                    UUID         PRIMARY KEY,
    investigation_id      UUID         NOT NULL UNIQUE REFERENCES investigation(id),
    autorite_destinataire VARCHAR(300) NOT NULL,
    transmitted_at        TIMESTAMP    NOT NULL,
    transmitted_by_id     UUID         NOT NULL REFERENCES agent(id),
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP,
    created_by_id         VARCHAR(100),
    updated_by_id         VARCHAR(100)
);

CREATE TABLE relance_suites (
    id                       UUID      PRIMARY KEY,
    transmission_autorite_id UUID      NOT NULL REFERENCES transmission_autorite(id),
    relance_at               TIMESTAMP NOT NULL,
    agent_id                 UUID      NOT NULL REFERENCES agent(id),
    contenu                  TEXT,
    version                  BIGINT    NOT NULL DEFAULT 0,
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP,
    created_by_id            VARCHAR(100),
    updated_by_id            VARCHAR(100)
);

CREATE INDEX idx_relance_suites_transmission
    ON relance_suites (transmission_autorite_id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'RELANCE_SUITES_TRANSMISSION', 'Délai avant relance formelle des suites données par l''autorité', 30, TRUE, TRUE, 0, now());

COMMENT ON TABLE transmission_autorite IS 'Transmission du dossier decide a l autorite competente (Lot 6 sous-chantier 1/5) - evenement factuel, une seule par investigation';
COMMENT ON TABLE relance_suites IS 'Relances formelles envoyees si l autorite destinataire ne repond pas - rattachees a une transmission_autorite';
