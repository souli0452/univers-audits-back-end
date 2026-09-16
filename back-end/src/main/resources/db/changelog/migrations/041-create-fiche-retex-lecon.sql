--liquibase formatted sql
--changeset dev:041-create-fiche-retex-lecon

CREATE TABLE fiche_retex (
    id                               UUID          PRIMARY KEY,
    investigation_id                 UUID          NOT NULL UNIQUE REFERENCES investigation(id),
    type_infraction_id               UUID          REFERENCES type_infraction(id),
    lieu                             VARCHAR(300),
    difficultes_rencontrees          TEXT,
    origine_soupcons                 TEXT,
    impact_financier                 NUMERIC(15,2),
    originalite_schemas              TEXT,
    collaborateurs_planifies         TEXT,
    jours_charges                    INTEGER,
    contexte                         TEXT,
    strategie_methodes               TEXT,
    synthese_resultats               TEXT          NOT NULL,
    enseignements_axes_amelioration  TEXT          NOT NULL,
    redige_par_id                    UUID          NOT NULL REFERENCES agent(id),
    version                          BIGINT        NOT NULL DEFAULT 0,
    created_at                       TIMESTAMP     NOT NULL,
    updated_at                       TIMESTAMP,
    created_by_id                    VARCHAR(100),
    updated_by_id                    VARCHAR(100)
);

CREATE TABLE lecon_a_partager (
    id                UUID         PRIMARY KEY,
    fiche_retex_id    UUID         NOT NULL UNIQUE REFERENCES fiche_retex(id),
    titre             VARCHAR(300) NOT NULL,
    resume            TEXT         NOT NULL,
    publiee_par_id    UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE fiche_retex IS 'Bilan retrospectif RETEX par investigation cloturee (Lot 9 sous-chantier 2/3) - une fiche par investigation';
COMMENT ON TABLE lecon_a_partager IS 'Extrait publie d une fiche RETEX, consultable largement en interne';
