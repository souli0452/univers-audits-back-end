--liquibase formatted sql
--changeset dev:005-create-type-infraction

CREATE TABLE type_infraction (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL,
    libelle             VARCHAR(300) NOT NULL,
    article_code_penal  VARCHAR(100),
    article_loi_004     VARCHAR(100),
    implique_ddip       BOOLEAN      NOT NULL DEFAULT FALSE,
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    ordre               INTEGER      DEFAULT 0,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by_id       VARCHAR(100),
    updated_by_id       VARCHAR(100)
);

CREATE UNIQUE INDEX idx_type_infraction_code ON type_infraction (code);

INSERT INTO type_infraction (id, code, libelle, implique_ddip, actif, ordre, version, created_at)
VALUES
    (gen_random_uuid(), 'ENRICHISSEMENT_ILLICITE', 'Enrichissement illicite', TRUE, TRUE, 1, 0, now()),
    (gen_random_uuid(), 'DEFAUT_FAUSSE_DECLARATION_PATRIMOINE', 'Défaut ou fausse déclaration d''intérêt ou de patrimoine', TRUE, TRUE, 2, 0, now());
