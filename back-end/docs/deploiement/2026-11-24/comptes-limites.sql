-- Comptes de base de données limités (audit OWASP du 2026-11-24, point S-07).
--
-- Aujourd'hui l'application ET Keycloak se connectent avec « postgres », le compte administrateur : une faille dans l'un
-- ou l'autre donnerait le contrôle de toute la base (lecture des deux jeux de données, commandes sur la machine).
-- Ce script crée deux comptes qui ne peuvent rien d'autre que leur propre travail :
--   asce_app       l'application : propriétaire des tables du schéma « public » (c'est ce qu'exige Liquibase pour migrer),
--                  aucun droit sur le schéma « keycloak » ;
--   asce_keycloak  Keycloak : propriétaire du schéma « keycloak », aucun droit sur les tables de l'application.
-- Aucun des deux n'est administrateur : pas de commande système (COPY … PROGRAM), pas de lecture de fichier, pas de
-- création de compte ou de base, pas de changement de configuration du serveur.
--
-- À lancer UNE FOIS, en tant que « postgres », sur la base de l'application, mots de passe fournis par psql (jamais en
-- clair dans une ligne de commande) — voir 2026-11-24-compte-base-limite.md. Relançable sans effet de bord.
-- Les données ne sont ni modifiées ni déplacées : seul le propriétaire des objets change.
-- Retour arrière : remettre « postgres » comme compte de connexion ; ce compte reste administrateur et lit toujours tout.

\set ON_ERROR_STOP on
\if :{?app_password}
\else
  \echo 'ARRET : variable app_password absente'
  \quit
\endif
\if :{?keycloak_password}
\else
  \echo 'ARRET : variable keycloak_password absente'
  \quit
\endif

-- 1. Les comptes ---------------------------------------------------------------------------------------------------------
SELECT 'CREATE ROLE asce_app LOGIN' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'asce_app') \gexec
SELECT 'CREATE ROLE asce_keycloak LOGIN' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'asce_keycloak') \gexec
ALTER ROLE asce_app      WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
ALTER ROLE asce_keycloak WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'keycloak_password';

-- 2. Accès à la base : seuls ces deux comptes (et les administrateurs) peuvent s'y connecter -----------------------------
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO asce_app, asce_keycloak', current_database()) \gexec

-- 3. Les schémas : chacun voit le sien, et seulement le sien ---------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS keycloak;
REVOKE ALL ON SCHEMA public   FROM PUBLIC;
REVOKE ALL ON SCHEMA keycloak FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO asce_app;
ALTER SCHEMA keycloak OWNER TO asce_keycloak;

-- 4. Propriété des objets existants : tables, vues, séquences, fonctions, types ------------------------------------------
-- (les séquences liées à une colonne suivent leur table ; une migration future ne peut modifier que ce qu'elle possède)
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT n.nspname AS sch, c.relname AS nom, c.relkind AS genre, t.proprietaire
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN (VALUES ('public', 'asce_app'), ('keycloak', 'asce_keycloak')) AS t(sch, proprietaire) ON t.sch = n.nspname
    WHERE c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S')
      AND pg_get_userbyid(c.relowner) <> t.proprietaire
      AND (c.relkind <> 'S' OR NOT EXISTS (SELECT 1 FROM pg_depend d
                                           WHERE d.objid = c.oid AND d.classid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')))
    ORDER BY CASE c.relkind WHEN 'S' THEN 2 ELSE 1 END
  LOOP
    EXECUTE format('ALTER %s %I.%I OWNER TO %I',
      CASE r.genre WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' WHEN 'f' THEN 'FOREIGN TABLE'
                   WHEN 'S' THEN 'SEQUENCE' ELSE 'TABLE' END,
      r.sch, r.nom, r.proprietaire);
  END LOOP;

  FOR r IN
    SELECT n.nspname AS sch, p.oid::regprocedure::text AS sig, t.proprietaire
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    JOIN (VALUES ('public', 'asce_app'), ('keycloak', 'asce_keycloak')) AS t(sch, proprietaire) ON t.sch = n.nspname
    WHERE pg_get_userbyid(p.proowner) <> t.proprietaire
      AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.classid = 'pg_proc'::regclass AND d.deptype = 'e')  -- hors extensions
  LOOP
    EXECUTE format('ALTER ROUTINE %s OWNER TO %I', r.sig, r.proprietaire);
  END LOOP;

  FOR r IN
    SELECT n.nspname AS sch, ty.typname AS nom, t.proprietaire
    FROM pg_type ty
    JOIN pg_namespace n ON n.oid = ty.typnamespace
    JOIN (VALUES ('public', 'asce_app'), ('keycloak', 'asce_keycloak')) AS t(sch, proprietaire) ON t.sch = n.nspname
    WHERE ty.typtype IN ('e', 'd')
      AND pg_get_userbyid(ty.typowner) <> t.proprietaire
      AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = ty.oid AND d.classid = 'pg_type'::regclass AND d.deptype = 'e')
  LOOP
    EXECUTE format('ALTER TYPE %I.%I OWNER TO %I', r.sch, r.nom, r.proprietaire);
  END LOOP;
END $$;

-- 5. Contrôle : rien ne doit rester à un autre propriétaire (attendu : 0 et 0) --------------------------------------------
\echo 'Objets restant a un autre proprietaire, schema public puis schema keycloak (attendu : 0 puis 0) :'
SELECT (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S') AND pg_get_userbyid(c.relowner) <> 'asce_app') AS public_restants,
       (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'keycloak' AND c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S') AND pg_get_userbyid(c.relowner) <> 'asce_keycloak') AS keycloak_restants;
