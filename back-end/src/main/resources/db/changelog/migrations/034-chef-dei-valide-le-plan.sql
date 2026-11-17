--liquibase formatted sql
--changeset dev:034-chef-dei-valide-le-plan

-- Décision du 2026-11-16 : le plan d'enquête est validé par le chef du DEI (manuel des procédures, processus C,
-- C.2.4.3 : « plan d'investigation validé par le chef DEI » ; workflow PGPD_GU V3, étape 17 : le DEI produit le plan
-- d'enquête opérationnel). Le CGEA ne le valide plus ; l'administrateur garde la permission.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission p ON p.permission_key = 'INVESTIGATION_PLAN_VALIDER'
WHERE r.role_key = 'CHEF_DEI'
ON CONFLICT DO NOTHING;

DELETE FROM role_permission rp
USING role_definition r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.role_key = 'CGEA'
  AND p.permission_key = 'INVESTIGATION_PLAN_VALIDER';
