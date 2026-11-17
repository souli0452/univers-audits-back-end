--liquibase formatted sql
--changeset dev:030-cge-registre-auditions

-- Décision du 2026-11-16 : le CGE consulte le registre des auditions, comme le CGEA et l'administrateur.
-- Le manuel des procédures (processus C, pages 54 à 56) limite l'accès aux dossiers d'investigation au CGE,
-- au CGEA et au chef du DEI ; le registre rassemble les procès-verbaux d'audition et de visite de terrain.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission p ON p.permission_key = 'REGISTRE_AUDITIONS_VOIR'
WHERE r.role_key = 'CGE'
ON CONFLICT DO NOTHING;
