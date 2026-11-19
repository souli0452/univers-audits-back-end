--liquibase formatted sql
--changeset dev:038-pieces-jointes-telechargement-et-suppleance

-- 1. Télécharger une pièce jointe devient un droit distinct de la consulter (décision du 2026-11-18).
--    Tous les rôles métier continuent à consulter et à écouter sur la plateforme ; seuls l'administrateur, le CGE, le
--    CGEA et le chef du DEI peuvent télécharger, et chaque téléchargement est inscrit au journal d'audit.
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

-- 2. Suppléance : le CGEA et le CGE, hiérarchiquement au-dessus de l'agent BRPD, peuvent faire son travail
--    (enregistrer et compléter un dossier, gérer parties visées et témoins, traiter les informations
--    préoccupantes). Ni administration, ni audit, ni identité protégée : seuls les droits de travail courant.
--    Chaque action reste inscrite à leur propre nom. Ajustable dans l'écran Rôles & Permissions.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission p ON p.permission_key IN (
    'DOSSIER_ENREGISTRER',
    'DOSSIER_MODIFIER',
    'DOSSIER_MES_DOSSIERS_VOIR',
    'PARTIE_VISEE_GERER',
    'PARTIE_VISEE_SUPPRIMER',
    'TEMOIN_GERER',
    'TEMOIN_SUPPRIMER',
    'PIECE_JOINTE_RECLASSER',
    'PIECE_JOINTE_SUPPRIMER',
    'SECTION_DOSSIER_TRAVAIL_GERER',
    'INFO_PREOCCUPANTE_AUTOSAISIR',
    'INFO_PREOCCUPANTE_CREER',
    'INFO_PREOCCUPANTE_TRAITER',
    'INFO_PREOCCUPANTE_VOIR')
WHERE r.role_key IN ('CGE', 'CGEA')
ON CONFLICT DO NOTHING;
