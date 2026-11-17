--liquibase formatted sql
--changeset dev:029-cge-cgea-metier-seulement

-- Décision du 2026-11-14 : ADMIN_DDIC garde tout (technique et métier) ; le CGE et le CGEA ne font que le métier.
-- Ils perdent les permissions d'administration (agents, rôles, accès par rôle), de paramétrage (types d'infraction,
-- indices de fraude, jours fériés, délais, points de checklist, portail), d'audit, et la file des notifications.
-- Les catégories « ADMIN », « DOSSIERS », etc. en majuscules sont les anciennes permissions indicatives de la
-- migration 004, désactivées par la 028 : leurs attributions sont retirées aussi, par cohérence.
DELETE FROM role_permission rp
USING role_definition r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.role_key IN ('CGE', 'CGEA')
  AND (p.category IN ('Administration', 'Paramétrage', 'Audit', 'ADMIN')
       OR p.permission_key IN ('NOTIFICATION_FILE_VOIR', 'NOTIFICATION_FILE_GERER'));
