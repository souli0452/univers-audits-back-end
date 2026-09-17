--liquibase formatted sql
--changeset dev:008-seed-indice-fraude

-- Restaure indice_fraude (Lot 9 sous-chantier 3/3), perdu lors de la
-- consolidation du schema en 001-baseline-schema.sql (2026-09-17) — seule
-- la structure de la table a ete capturee, pas les 6 typologies
-- canoniques amorcees a l'origine.

INSERT INTO indice_fraude (id, code, libelle, categorie, description, actif, ordre, version, created_at)
VALUES
    (gen_random_uuid(), 'MP_SURFACTURATION', 'Surfacturation ou écart de prix anormal sur un marché public', 'Marchés publics', NULL, TRUE, 1, 0, now()),
    (gen_random_uuid(), 'PS_CONFLIT_INTERET', 'Conflit d''intérêt non déclaré sur un poste sensible', 'Postes sensibles', NULL, TRUE, 2, 0, now()),
    (gen_random_uuid(), 'RE_ECART_CAISSE', 'Écart de caisse récurrent non justifié', 'Régies', NULL, TRUE, 3, 0, now()),
    (gen_random_uuid(), 'SU_DETOURNEMENT_OBJET', 'Détournement de l''objet d''une subvention accordée', 'Subventions', NULL, TRUE, 4, 0, now()),
    (gen_random_uuid(), 'GB_ENGAGEMENT_IRREGULIER', 'Engagement de dépense sans autorisation budgétaire régulière', 'Gestion budgétaire', NULL, TRUE, 5, 0, now()),
    (gen_random_uuid(), 'DP_ENRICHISSEMENT_INEXPLIQUE', 'Enrichissement inexpliqué constaté entre deux déclarations de patrimoine', 'Déclarations de patrimoine', NULL, TRUE, 6, 0, now());
