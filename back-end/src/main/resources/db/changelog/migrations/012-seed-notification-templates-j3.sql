--liquibase formatted sql
--changeset dev:012-seed-notification-templates-j3

-- Modeles de texte pour les 5 nouvelles alertes du sous-chantier "alertes de
-- delai J-3 et a echeance" (voir docs/superpowers/specs/2026-09-18-alertes-delai-j3-design.md).
-- Meme structure de colonnes que 005-seed-portal-config.sql. PortalConfig
-- n'etend pas AuditEntity : id/updated_at/version doivent etre fournis
-- explicitement (pas de DEFAULT au niveau base).

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
(gen_random_uuid(), 'notif_subject_deadline_ar_j3',
 'ALERTE : Accusé de réception à échéance dans 3 jours — {numero}',
 'Délai AR à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai légal d''accusé de réception. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_ar_j3',
 'Le délai légal de 7 jours pour l''envoi de l''accusé de réception B5 arrive à échéance dans 3 jours pour le dossier {numero}. Traiter avant le dépassement.',
 'Délai AR à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai légal d''accusé de réception. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_complement_j3',
 'ALERTE : Complément d''information à échéance dans 3 jours — {numero}',
 'Délai complément à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai de réception d''un complément d''information. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_complement_j3',
 'Le délai de réception du complément d''information arrive à échéance dans 3 jours pour le dossier {numero}.',
 'Délai complément à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai de réception d''un complément d''information. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_investigation_j3',
 'ALERTE : Investigation à échéance dans 3 jours — {numero}',
 'Délai investigation à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai réglementaire d''investigation. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_investigation_j3',
 'L''investigation du dossier {numero} arrive à échéance dans 3 jours. Anticiper une éventuelle prolongation avec le CGEA et le CGE.',
 'Délai investigation à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai réglementaire d''investigation. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_demande_documents',
 'ALERTE : Délai de réponse à une demande de documents dépassé — {numero}',
 'Délai demande documents dépassé — sujet',
 'Alerte automatique quand le délai de réponse à une demande de documents est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_demande_documents',
 'Le délai de réponse du destinataire à une demande de documents est dépassé pour le dossier {numero}. Une escalade peut être engagée.',
 'Délai demande documents dépassé — contenu',
 'Alerte automatique quand le délai de réponse à une demande de documents est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_demande_documents_j3',
 'ALERTE : Délai de réponse à une demande de documents à échéance dans 3 jours — {numero}',
 'Délai demande documents à échéance J-3 — sujet',
 'Alerte automatique 3 jours avant l''échéance du délai de réponse à une demande de documents. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_demande_documents_j3',
 'Le délai de réponse du destinataire à une demande de documents arrive à échéance dans 3 jours pour le dossier {numero}.',
 'Délai demande documents à échéance J-3 — contenu',
 'Alerte automatique 3 jours avant l''échéance du délai de réponse à une demande de documents. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0);
