--liquibase formatted sql
--changeset dev:003-seed-parametre-delai

-- Restaure les 14 parametres de delai qui etaient inseres par les anciennes
-- migrations 004/008/021/024/033/035/036/037 (supprimees lors de la
-- consolidation du schema en 001-baseline-schema.sql, 2026-09-17). Aucune
-- de ces migrations ne recreait ces lignes de donnees - uniquement le
-- CREATE TABLE. Regression trouvee et corrigee avant qu'elle ne soit
-- livree : sans ces lignes, resolveDelaiJours() leve une exception
-- attrapee silencieusement (loguee en warning) dans tous les services
-- calculant une echeance, qui renvoient alors null partout.

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'ACCUSE_RECEPTION', 'Délai d''accusé de réception du dossier', 7, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_COMPLEMENT', 'Délai de réponse à une demande de complément d''information', 14, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'INVESTIGATION_DUREE_DEFAUT', 'Durée par défaut d''une investigation', 90, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGE', 'Délai d''approbation du CGE', 20, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_INITIAL', 'Délai de réponse à une demande initiale de documents', NULL, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_RELANCE', 'Délai de réponse après relance', NULL, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'VALIDATION_PLAN_INVESTIGATION_DEI', 'Délai de validation du plan d''investigation par le DEI, après délivrance du mandat', 8, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE', 'Délai avant saisine judiciaire (immédiat après sommation infructueuse)', 0, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'REVUE_CJ_RAPPORT', 'Délai de revue du rapport par le conseiller juridique', 10, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ANALYSE_DEI_RAPPORT', 'Délai d''analyse du rapport par le DEI', 15, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGEA_RAPPORT', 'Délai d''approbation du rapport par le CGEA', 10, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'RELANCE_SUITES_TRANSMISSION', 'Délai avant relance formelle des suites données par l''autorité', 30, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'PLAN_ACTIONS_ENTITE_CONTROLEE', 'Délai de dépôt du plan d''actions par l''entité contrôlée après note de recommandations', 20, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'MISSION_SUIVI_PLAN_ACTIONS', 'Délai pour mener une mission de suivi après le dépôt du plan d''actions', 365, FALSE, TRUE, 0, now());
