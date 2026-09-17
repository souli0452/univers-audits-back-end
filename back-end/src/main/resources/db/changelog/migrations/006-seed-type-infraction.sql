--liquibase formatted sql
--changeset dev:006-seed-type-infraction

-- Restaure type_infraction, perdu lors de la consolidation du schema en
-- 001-baseline-schema.sql (2026-09-17) — seule la structure de la table a
-- ete capturee, pas ces 2 lignes de reference.

INSERT INTO type_infraction (id, code, libelle, implique_ddip, actif, ordre, version, created_at)
VALUES
    (gen_random_uuid(), 'ENRICHISSEMENT_ILLICITE', 'Enrichissement illicite', TRUE, TRUE, 1, 0, now()),
    (gen_random_uuid(), 'DEFAUT_FAUSSE_DECLARATION_PATRIMOINE', 'Défaut ou fausse déclaration d''intérêt ou de patrimoine', TRUE, TRUE, 2, 0, now());
