--liquibase formatted sql
--changeset dev:042-create-indice-fraude

CREATE TABLE indice_fraude (
    id             UUID          PRIMARY KEY,
    code           VARCHAR(50)   NOT NULL,
    libelle        VARCHAR(300)  NOT NULL,
    categorie      VARCHAR(100),
    description    TEXT,
    actif          BOOLEAN       NOT NULL DEFAULT TRUE,
    ordre          INTEGER,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP,
    created_by_id  VARCHAR(100),
    updated_by_id  VARCHAR(100)
);

CREATE UNIQUE INDEX idx_indice_fraude_code ON indice_fraude(code);

COMMENT ON TABLE indice_fraude IS 'Referentiel des indices et typologies de fraude, exploitable comme aide a l enquete et comme grille de cartographie des risques par categorie (Lot 9 sous-chantier 3/3)';
