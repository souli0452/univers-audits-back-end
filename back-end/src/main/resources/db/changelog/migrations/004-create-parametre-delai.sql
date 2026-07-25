--liquibase formatted sql
--changeset dev:004-create-parametre-delai

CREATE TABLE parametre_delai (
    id               UUID PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    libelle          VARCHAR(300) NOT NULL,
    valeur_jours     INTEGER,
    jours_ouvrables  BOOLEAN      NOT NULL DEFAULT TRUE,
    actif            BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    created_by_id    VARCHAR(100),
    updated_by_id    VARCHAR(100)
);

CREATE UNIQUE INDEX idx_parametre_delai_code ON parametre_delai (code);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'ACCUSE_RECEPTION', 'Délai d''accusé de réception du dossier', 7, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_COMPLEMENT', 'Délai de réponse à une demande de complément d''information', 14, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'INVESTIGATION_DUREE_DEFAUT', 'Durée par défaut d''une investigation', 90, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGE', 'Délai d''approbation du CGE', 20, TRUE, TRUE, 0, now());
