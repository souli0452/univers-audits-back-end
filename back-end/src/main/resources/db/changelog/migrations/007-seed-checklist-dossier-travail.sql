--liquibase formatted sql
--changeset dev:007-seed-checklist-dossier-travail

-- Restaure point_checklist_dossier_travail, perdu lors de la consolidation
-- du schema en 001-baseline-schema.sql (2026-09-17) — seule la structure
-- de la table a ete capturee. submitReport() bloque tant qu'un point actif
-- reste non coche (checklist_dossier_travail_coche).
--
-- Contenu PROVISOIRE : le manuel de procedures ASCE-LC listant les 22 points
-- reels n'est pas disponible dans ce depot (voir spec 2026-08-13). Ces
-- libelles doivent etre corriges via PUT /api/v1/points-checklist-dossier-travail/{code}
-- avant tout usage reel.
INSERT INTO point_checklist_dossier_travail (id, code, libelle, categorie, ordre, actif, version, created_at)
SELECT gen_random_uuid(),
       'PT-' || LPAD(n::text, 2, '0'),
       'Point de contrôle ' || n || ' — contenu à confirmer avec le manuel de procédures ASCE-LC',
       'PROVISOIRE',
       n,
       TRUE,
       0,
       now()
FROM generate_series(1, 22) AS n;
