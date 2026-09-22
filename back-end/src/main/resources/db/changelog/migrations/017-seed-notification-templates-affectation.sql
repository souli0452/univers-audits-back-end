--liquibase formatted sql
--changeset dev:017-seed-notification-templates-affectation

-- Modele de texte pour la notification d'affectation d'un dossier
-- (NotificationType.AFFECTATION_DOSSIER, ajoute en migration 016).
-- Voir docs/superpowers/specs/2026-09-22-fiche-affectation-design.md.
INSERT INTO portal_config
(
    id, config_key, config_value, label, description,
    value_type, group_name, updated_at, version
)
VALUES
(gen_random_uuid(), 'notif_subject_affectation_dossier',
 'Dossier {numero} affecté — {departementOuAgent}',
 'Affectation dossier — sujet',
 'Notification envoyée au département/agent désigné quand le CGEA valide l''affectation d''un dossier. Utilisez {numero} pour le numéro du dossier et {departementOuAgent} pour le libellé du destinataire (département ou agent nommé).',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_affectation_dossier',
 'Le dossier {numero} vous a été affecté ({departementOuAgent}) suite à la décision du CGEA. Merci de le prendre en charge.',
 'Affectation dossier — contenu',
 'Notification envoyée au département/agent désigné quand le CGEA valide l''affectation d''un dossier. Utilisez {numero} pour le numéro du dossier et {departementOuAgent} pour le libellé du destinataire (département ou agent nommé).',
 'TEXT', 'NOTIFICATIONS', now(), 0);
