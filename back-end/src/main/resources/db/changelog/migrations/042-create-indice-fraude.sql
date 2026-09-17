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

INSERT INTO indice_fraude (id, code, libelle, categorie, description, actif, ordre, version, created_at)
VALUES
    (gen_random_uuid(), 'MP_SURFACTURATION', 'Surfacturation ou écart de prix anormal sur un marché public', 'Marchés publics', NULL, TRUE, 1, 0, now()),
    (gen_random_uuid(), 'PS_CONFLIT_INTERET', 'Conflit d''intérêt non déclaré sur un poste sensible', 'Postes sensibles', NULL, TRUE, 2, 0, now()),
    (gen_random_uuid(), 'RE_ECART_CAISSE', 'Écart de caisse récurrent non justifié', 'Régies', NULL, TRUE, 3, 0, now()),
    (gen_random_uuid(), 'SU_DETOURNEMENT_OBJET', 'Détournement de l''objet d''une subvention accordée', 'Subventions', NULL, TRUE, 4, 0, now()),
    (gen_random_uuid(), 'GB_ENGAGEMENT_IRREGULIER', 'Engagement de dépense sans autorisation budgétaire régulière', 'Gestion budgétaire', NULL, TRUE, 5, 0, now()),
    (gen_random_uuid(), 'DP_ENRICHISSEMENT_INEXPLIQUE', 'Enrichissement inexpliqué constaté entre deux déclarations de patrimoine', 'Déclarations de patrimoine', NULL, TRUE, 6, 0, now());
