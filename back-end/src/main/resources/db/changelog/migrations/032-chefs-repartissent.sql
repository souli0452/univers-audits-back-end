--liquibase formatted sql
--changeset dev:032-chefs-repartissent

-- Le chef d'un département répartit à ses agents les dossiers que le CGEA lui a affectés (fiche d'affectation).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission p ON p.permission_key = 'FICHE_AFFECTATION_REPARTIR'
WHERE r.role_key IN ('CHEF_DEI', 'CHEF_DAC')
ON CONFLICT DO NOTHING;
