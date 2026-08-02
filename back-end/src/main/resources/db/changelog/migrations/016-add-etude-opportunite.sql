--liquibase formatted sql
--changeset dev:016-add-etude-opportunite

CREATE TABLE etude_opportunite (
    id                                              UUID PRIMARY KEY,
    version                                         BIGINT NOT NULL DEFAULT 0,
    created_at                                       TIMESTAMP NOT NULL,
    updated_at                                       TIMESTAMP,
    created_by_id                                    VARCHAR(100),
    updated_by_id                                    VARCHAR(100),
    dossier_id                                       UUID NOT NULL UNIQUE REFERENCES dossier(id),
    preoccupation_reelle                             BOOLEAN,
    preoccupation_reelle_commentaire                 TEXT,
    competence_asce_lc                               BOOLEAN,
    competence_asce_lc_commentaire                   TEXT,
    nature_qualification                             VARCHAR(20),
    type_infraction_id                               UUID REFERENCES type_infraction(id),
    qualification_non_penale                         VARCHAR(30),
    preuves_suffisantes                              BOOLEAN,
    preuves_suffisantes_commentaire                  TEXT,
    enquete_complementaire_necessaire                BOOLEAN,
    enquete_complementaire_necessaire_commentaire    TEXT,
    urgence_securisation_preuves                     BOOLEAN,
    urgence_securisation_preuves_commentaire         TEXT,
    opportunite_saisir_procureur                     BOOLEAN,
    opportunite_saisir_procureur_commentaire         TEXT,
    secteur_sensible                                 BOOLEAN,
    secteur_precision                                VARCHAR(300),
    solidite_allegation                              BOOLEAN,
    solidite_allegation_commentaire                  TEXT,
    avis_general                                     VARCHAR(5000)
);

COMMENT ON TABLE etude_opportunite IS 'Grille structuree de 9 questions du conseiller juridique (Lot 2, plan de travail S11) - une par dossier';
COMMENT ON COLUMN etude_opportunite.qualification_non_penale IS 'Renseigne si nature_qualification=ADMINISTRATIVE - prepare la branche ORIENTEE_ADMINISTRATIF (sous-chantier DecisionCGE, pas encore implemente)';
