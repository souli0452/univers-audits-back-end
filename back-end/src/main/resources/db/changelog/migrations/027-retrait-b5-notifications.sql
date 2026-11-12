--liquibase formatted sql
--changeset dev:027-retrait-b5-notifications

-- « B5 » est le nom interne du formulaire d'accusé de réception : il n'apparaît plus dans les textes
-- (comme « B4 » dans la migration 026). Les migrations 005 et 012 ne sont pas modifiées (somme de contrôle).
UPDATE portal_config
SET config_value = replace(config_value, ' B5', '')
WHERE config_key IN ('notif_content_deadline_ar', 'notif_content_deadline_ar_j3')
  AND config_value LIKE '% B5%';

UPDATE portal_config
SET label = replace(label, ' B5', '')
WHERE config_key IN ('notif_subject_acknowledgment_b5', 'notif_content_acknowledgment_b5')
  AND label LIKE '% B5%';
