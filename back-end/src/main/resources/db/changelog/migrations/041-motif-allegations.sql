--liquibase formatted sql
--changeset dev:041-motif-allegations

-- Le citoyen choisit désormais l'allégation officielle par son nom (les 15 de la liste de l'ASCE-LC), ou « Autre » avec
-- un texte libre obligatoire (150 mots au maximum, contrôlé par le serveur).
-- Les anciennes valeurs (mots simples de la migration 040) sont reprises sous le nom de l'allégation correspondante.
ALTER TABLE dossier DROP CONSTRAINT dossier_motif_declare_check;

UPDATE dossier SET motif_declare = 'DETOURNEMENT_MALVERSATIONS' WHERE motif_declare = 'DETOURNEMENT';
UPDATE dossier SET motif_declare = 'FAUX_ET_USAGE_DE_FAUX'      WHERE motif_declare = 'FAUX';
UPDATE dossier SET motif_declare = 'ENRICHISSEMENT_ILLICITE'    WHERE motif_declare = 'ENRICHISSEMENT';
UPDATE dossier SET motif_declare = 'CONFLIT_FONCIER'            WHERE motif_declare = 'FONCIER';

ALTER TABLE dossier ADD CONSTRAINT dossier_motif_declare_check CHECK (motif_declare IS NULL
    OR motif_declare IN ('CONFLIT_INTERET', 'BLANCHIMENT_CAPITAUX', 'FAVORITISME', 'ENRICHISSEMENT_ILLICITE',
                         'ABUS_DE_FONCTION', 'DEMANDE_INTERVENTION_DETTES', 'FRAUDE', 'CONCUSSION', 'DELIT_APPARENCE',
                         'CONFLIT_FONCIER', 'CORRUPTION', 'DETOURNEMENT_MALVERSATIONS', 'STELLIONAT',
                         'DYSFONCTIONNEMENT', 'FAUX_ET_USAGE_DE_FAUX', 'AUTRE'));

-- Texte libre du motif « Autre » (150 mots, environ 1 200 caractères ; marge pour les accents et la ponctuation).
ALTER TABLE dossier ADD COLUMN motif_autre VARCHAR(2000);
