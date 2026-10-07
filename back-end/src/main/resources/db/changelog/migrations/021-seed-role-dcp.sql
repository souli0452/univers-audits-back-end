--liquibase formatted sql
--changeset dev:021-seed-role-dcp

-- Rôle DCP (Direction de la Communication et de la Presse) : accès en lecture aux seules statistiques
-- globales (agrégats, sans dossier ni donnée personnelle). Étape 34 du workflow PGPD_GU V3.
-- Ce script ne crée que la définition affichée dans « Rôles & Permissions » : le rôle de royaume
-- « DCP » doit aussi exister dans Keycloak pour pouvoir être attribué à un agent.
INSERT INTO role_definition
    (id, role_key, label, description, icon, severity, display_order,
     visible, active, is_protected, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'DCP',
     'Communication (DCP)',
     'Direction de la Communication et de la Presse : consulte les statistiques globales, sans accès aux dossiers.',
     'pi pi-megaphone', 'secondary', 8, TRUE, TRUE, FALSE, now(), now(), 0)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'DCP'
  AND p.permission_key IN ('VOIR_STATISTIQUES', 'EXPORTER_STATISTIQUES')
ON CONFLICT DO NOTHING;

--rollback DELETE FROM role_permission WHERE role_id IN (SELECT id FROM role_definition WHERE role_key = 'DCP');
--rollback DELETE FROM role_definition WHERE role_key = 'DCP';
