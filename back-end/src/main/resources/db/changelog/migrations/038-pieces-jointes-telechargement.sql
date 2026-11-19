--liquibase formatted sql
--changeset dev:038-pieces-jointes-telechargement

-- Télécharger une pièce jointe devient un droit distinct de la consulter (décision du 2026-11-18).
-- Tous les rôles métier continuent à consulter et à écouter sur la plateforme ; seuls l'administrateur, le CGE, le
-- CGEA et le chef du DEI peuvent télécharger, et chaque téléchargement est inscrit au journal d'audit.
--
-- Décision du 2026-11-19 : aucune suppléance entre rôles. Chacun reste responsable des tâches qui lui sont
-- attribuées (le CGE et le CGEA n'enregistrent ni ne complètent un dossier à la place de l'agent BRPD), pour
-- éviter les abus de pouvoir. Une absence se gère en attribuant un rôle par l'écran Agents (opération tracée).
INSERT INTO permission (id, permission_key, label, category, active, created_at) VALUES
    (gen_random_uuid(), 'PIECE_JOINTE_TELECHARGER', 'Télécharger une pièce jointe', 'Dossiers', TRUE, now())
ON CONFLICT (permission_key) DO UPDATE
    SET label = EXCLUDED.label, category = EXCLUDED.category, active = TRUE;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission p ON p.permission_key = 'PIECE_JOINTE_TELECHARGER'
WHERE r.role_key IN ('ADMIN_DDIC', 'CGE', 'CGEA', 'CHEF_DEI')
ON CONFLICT DO NOTHING;
