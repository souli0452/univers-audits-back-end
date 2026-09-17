--liquibase formatted sql
--changeset dev:005-seed-portal-config

-- Restaure portal_config (identite/contact + modeles de notification),
-- perdu lors de la consolidation du schema en 001-baseline-schema.sql
-- (2026-09-17) — seule la structure de la table a ete capturee.
-- resolveNotificationText() depend de ces lignes pour tous les envois de
-- notification (DossierServiceImpl, InvestigationServiceImpl, etc.).
--
-- PortalConfig n'etend pas AuditEntity (entite independante) : id/updated_at/
-- version n'ont pas de DEFAULT au niveau base, doivent etre fournis
-- explicitement (contrairement a la migration d'origine, supprimee, qui
-- declarait ses propres DEFAULT dans son CREATE TABLE).

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


(
    gen_random_uuid(),
    'site_name',
    'INTÉGRITÉ+',
    'Nom du site',
    'Nom affiché dans la navbar et les onglets',
    'TEXT',
    'IDENTITE',
    now(), 0
),

(
    gen_random_uuid(),
    'site_tagline',
    'La Patrie ou la Mort, nous vaincrons',
    'Slogan footer',
    'Slogan affiché en bas du footer',
    'TEXT',
    'IDENTITE',
    now(), 0
),

(
    gen_random_uuid(),
    'logo_integrite',
    '/assets/logo-integrite.png',
    'Logo principal',
    'Logo INTÉGRITÉ+ (navbar, footer)',
    'IMAGE_URL',
    'IDENTITE',
    now(), 0
),

(
    gen_random_uuid(),
    'logo_asce',
    '/assets/logo-asce.png',
    'Logo ASCE-LC',
    'Logo ASCE-LC (footer uniquement)',
    'IMAGE_URL',
    'IDENTITE',
    now(), 0
),

-- CONTACT
(
    gen_random_uuid(),
    'hotline_number',
    '80 00 11 11',
    'Numéro vert',
    'Numéro affiché dans la barre supérieure et footer',
    'PHONE',
    'CONTACT',
    now(), 0
),

(
    gen_random_uuid(),
    'hotline_label',
    'N° VERT',
    'Libellé numéro vert',
    'Texte avant le numéro',
    'TEXT',
    'CONTACT',
    now(), 0
),

(
    gen_random_uuid(),
    'email_contact',
    'contact@asce-lc.bf',
    'Email de contact',
    'Email affiché dans le footer',
    'TEXT',
    'CONTACT',
    now(), 0
),

(
    gen_random_uuid(),
    'website_url',
    'https://www.asce-lc.bf',
    'Site web officiel',
    'URL du site ASCE-LC',
    'URL',
    'CONTACT',
    now(), 0
),

(
    gen_random_uuid(),
    'address',
    'Ouagadougou, Burkina Faso',
    'Adresse',
    'Adresse physique affichée dans le footer',
    'TEXT',
    'CONTACT',
    now(), 0
)

ON CONFLICT (config_key)
DO NOTHING;

-- Modeles de notification (sujets/contenus configurables, {numero}/{motif}/
-- {role}/{objet} remplaces par les valeurs reelles au moment de l'envoi).

INSERT INTO portal_config
(id, config_key, config_value, label, description, value_type, group_name, updated_at, version)
VALUES

(gen_random_uuid(), 'notif_subject_audio_alert',
 'ALERTE — Dénonciation audio à traiter',
 'Alerte agent — sujet',
 'Notification interne quand une dénonciation vocale est soumise',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_audio_alert',
 'Un citoyen a soumis une dénonciation vocale. Veuillez écouter l''enregistrement et constituer le dossier.',
 'Alerte agent — contenu',
 'Notification interne quand une dénonciation vocale est soumise',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_protection_submitted',
 '⚠ PROTECTION LANCEUR D''ALERTE — Soumission reçue',
 'Protection à la soumission — sujet',
 'Notification interne quand la protection lanceur d''alerte est invoquée dès le dépôt',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_protection_submitted',
 'Un déclarant a invoqué la protection lanceur d''alerte (Loi N°010-2004/AN) dès la soumission. Le dossier a été automatiquement marqué confidentiel.',
 'Protection à la soumission — contenu',
 'Notification interne quand la protection lanceur d''alerte est invoquée dès le dépôt',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_protection_registered',
 'PROTECTION LANCEUR D''ALERTE — Dossier {numero}',
 'Protection à l''enregistrement — sujet',
 'Notification interne à l''enregistrement du dossier. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_protection_registered',
 'Le déclarant du dossier {numero} a invoqué la protection lanceur d''alerte (Loi N°010-2004/AN). Veuillez prendre les mesures de protection appropriées.',
 'Protection à l''enregistrement — contenu',
 'Notification interne à l''enregistrement du dossier. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_receipt_b4',
 'Récépissé de dépôt — {numero}',
 'Récépissé B4 — sujet',
 'Confirmation d''enregistrement envoyée au déclarant. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_receipt_b4',
 'Votre dossier a été enregistré. Le code de suivi vous a été communiqué séparément lors de votre soumission.',
 'Récépissé B4 — contenu',
 'Confirmation d''enregistrement envoyée au déclarant',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_acknowledgment_b5',
 'Accusé de réception — votre dossier',
 'Accusé de réception B5 — sujet',
 'Notification au déclarant confirmant la réception officielle du dossier',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_acknowledgment_b5',
 'L''ASCE-LC accuse réception de votre dossier et vous informera des suites dans les meilleurs délais.',
 'Accusé de réception B5 — contenu',
 'Notification au déclarant confirmant la réception officielle du dossier',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_complement_request',
 'Information complémentaire requise — votre dossier',
 'Demande de complément — sujet',
 'Notification au déclarant lorsqu''un complément d''information est demandé',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_complement_request',
 'L''ASCE-LC a besoin d''informations supplémentaires. Motif : {motif}',
 'Demande de complément — contenu',
 'Notification au déclarant lorsqu''un complément d''information est demandé. Utilisez {motif} pour insérer le motif saisi par l''agent.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_investigation_assignment',
 'Vous avez été affecté(e) à l''investigation — {numero}',
 'Affectation investigation — sujet',
 'Notification à l''agent affecté à une investigation. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_investigation_assignment',
 'Rôle : {role} · Dossier : {objet}',
 'Affectation investigation — contenu',
 'Notification à l''agent affecté à une investigation. Utilisez {role} pour le rôle attribué et {objet} pour l''objet du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_ar',
 'ALERTE : Délai dépassé — {numero}',
 'Délai AR dépassé — sujet',
 'Alerte automatique quand le délai légal d''accusé de réception (7 jours) est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_ar',
 'Le délai légal de 7 jours pour l''envoi de l''accusé de réception B5 est dépassé pour le dossier {numero}. Action requise immédiatement.',
 'Délai AR dépassé — contenu',
 'Alerte automatique quand le délai légal d''accusé de réception (7 jours) est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_complement',
 'ALERTE : Complément non reçu — {numero}',
 'Délai complément dépassé — sujet',
 'Alerte automatique quand le délai de réception d''un complément d''information (14 jours) est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_complement',
 'Le délai de 14 jours pour recevoir le complément d''information est dépassé pour le dossier {numero}.',
 'Délai complément dépassé — contenu',
 'Alerte automatique quand le délai de réception d''un complément d''information (14 jours) est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),

(gen_random_uuid(), 'notif_subject_deadline_investigation',
 'ALERTE : Investigation dépassée — {numero}',
 'Délai investigation dépassé — sujet',
 'Alerte automatique quand le délai réglementaire d''investigation (90 jours) est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_deadline_investigation',
 'L''investigation du dossier {numero} dépasse le délai réglementaire de 90 jours. Une prolongation doit être validée par le CGEA et le CGE.',
 'Délai investigation dépassé — contenu',
 'Alerte automatique quand le délai réglementaire d''investigation (90 jours) est dépassé. Utilisez {numero} pour insérer le numéro du dossier.',
 'TEXT', 'NOTIFICATIONS', now(), 0)

ON CONFLICT (config_key)
DO NOTHING;
