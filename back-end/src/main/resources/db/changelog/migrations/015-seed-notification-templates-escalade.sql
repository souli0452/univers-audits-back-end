--liquibase formatted sql
--changeset dev:015-seed-notification-templates-escalade

-- Modeles de texte pour les 4 nouvelles alertes d'escalade automatique
-- vers CGEA/CGE (voir docs/superpowers/specs/2026-09-22-escalade-automatique-design.md).
-- Meme structure de colonnes que 005-seed-portal-config.sql/012-seed-notification-templates-j3.sql.
-- PortalConfig n'etend pas AuditEntity : id/updated_at/version doivent etre
-- fournis explicitement (pas de DEFAULT au niveau base).

INSERT INTO portal_config
(
    id,
    config_key,
    config_value,
    label,
    description,
    value_type,
    group_name,
    updated_at,
    version
)
VALUES
(gen_random_uuid(), 'notif_subject_escalade_ar',
 'ESCALADE : Accusé de réception toujours en retard — {numero} ({agentEnCharge})',
 'Escalade AR — sujet',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en dépassement de délai d''accusé de réception au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_ar',
 'Le dossier {numero} dépasse le délai légal d''accusé de réception depuis plusieurs jours, malgré l''alerte envoyée à {agentEnCharge}. Une intervention de la hiérarchie est requise.',
 'Escalade AR — contenu',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en dépassement de délai d''accusé de réception au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_escalade_complement',
 'ESCALADE : Complément d''information toujours en retard — {numero} ({agentEnCharge})',
 'Escalade complément — sujet',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en attente de complément d''information au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_complement',
 'Le dossier {numero} reste en attente de complément d''information au-delà du délai de grâce, malgré l''alerte envoyée à {agentEnCharge}. Une intervention de la hiérarchie est requise.',
 'Escalade complément — contenu',
 'Alerte automatique vers CGEA/CGE quand un dossier reste en attente de complément d''information au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_escalade_investigation',
 'ESCALADE : Investigation toujours en dépassement — {numero} ({agentEnCharge})',
 'Escalade investigation — sujet',
 'Alerte automatique vers CGEA/CGE quand une investigation dépasse son échéance au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_investigation',
 'L''investigation du dossier {numero}, menée par {agentEnCharge}, dépasse son échéance au-delà du délai de grâce. Une intervention de la hiérarchie est requise.',
 'Escalade investigation — contenu',
 'Alerte automatique vers CGEA/CGE quand une investigation dépasse son échéance au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_escalade_demande_documents',
 'ESCALADE : Demande de documents toujours sans réponse — {numero} ({agentEnCharge})',
 'Escalade demande documents — sujet',
 'Alerte automatique vers CGEA/CGE quand une demande de documents reste sans réponse au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_escalade_demande_documents',
 'La demande de documents du dossier {numero}, suivie par {agentEnCharge}, reste sans réponse au-delà du délai de grâce. Une intervention de la hiérarchie est requise.',
 'Escalade demande documents — contenu',
 'Alerte automatique vers CGEA/CGE quand une demande de documents reste sans réponse au-delà du délai de grâce. Utilisez {numero} pour le numéro du dossier et {agentEnCharge} pour le nom de l''agent en charge.',
 'TEXT', 'NOTIFICATIONS', now(), 0);
