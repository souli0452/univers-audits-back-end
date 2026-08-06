--liquibase formatted sql
--changeset dev:024-add-demande-documents-saisine-judiciaire-delai

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE', 'Délai avant saisine judiciaire (immédiat après sommation infructueuse)', 0, TRUE, TRUE, 0, now());
