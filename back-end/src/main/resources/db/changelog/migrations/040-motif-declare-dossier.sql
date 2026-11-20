--liquibase formatted sql
--changeset dev:040-motif-declare-dossier

-- Motif choisi par le citoyen en mots simples (facultatif). Il ne remplace pas l'allégation officielle : celle-ci
-- reste retenue par l'agent à l'étude d'opportunité, et seule elle entre dans les statistiques publiées.
ALTER TABLE dossier ADD COLUMN motif_declare VARCHAR(40);

ALTER TABLE dossier ADD CONSTRAINT dossier_motif_declare_check CHECK (motif_declare IS NULL
    OR motif_declare IN ('CORRUPTION', 'DETOURNEMENT', 'FAVORITISME', 'FAUX', 'FRAUDE', 'ENRICHISSEMENT',
                         'ABUS_DE_FONCTION', 'FONCIER', 'DYSFONCTIONNEMENT', 'AUTRE'));
