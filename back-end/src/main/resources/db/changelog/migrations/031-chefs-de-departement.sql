--liquibase formatted sql
--changeset dev:031-chefs-de-departement

-- Décision du 2026-11-16 : « C.DEI » (étape 22 du workflow PGPD_GU V3) = chef du DEI. Le manuel des procédures
-- (processus C) lui donne aussi l'accès à l'archive des dossiers d'investigation.
--
-- Deux rôles de chef de département, à cumuler avec « Contrôleur d'État » (la politique de cumul l'exige) : ils
-- n'ajoutent que leurs pouvoirs propres. Le rôle Keycloak correspondant est créé au démarrage du back
-- (KeycloakRoleSynchronizer) ou par l'écran Rôles & Permissions.
INSERT INTO role_definition
    (id, role_key, label, description, icon, severity, display_order,
     visible, active, is_protected, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'CHEF_DEI',
     'Chef du DEI',
     'Chef du Département d''Enquête et d''Investigation : approuve les rapports de synthèse (étape 22 du workflow) et consulte le registre des auditions. À cumuler avec Contrôleur d''État.',
     'pi pi-briefcase', 'warn', 10, TRUE, TRUE, FALSE, now(), now(), 0),

    (gen_random_uuid(), 'CHEF_DAC',
     'Chef du DAC',
     'Chef du Département d''Audit et de Contrôle. À cumuler avec Contrôleur d''État ; n''a pas de pouvoir propre pour l''instant.',
     'pi pi-briefcase', 'info', 11, TRUE, TRUE, FALSE, now(), now(), 0)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission p ON p.permission_key IN ('INVESTIGATION_DEI_STATUER', 'REGISTRE_AUDITIONS_VOIR')
WHERE r.role_key = 'CHEF_DEI'
ON CONFLICT DO NOTHING;

-- L'approbation DEI quitte le CGEA : le workflow la donne au chef du DEI. L'administrateur la garde.
DELETE FROM role_permission rp
USING role_definition r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.role_key = 'CGEA'
  AND p.permission_key = 'INVESTIGATION_DEI_STATUER';
