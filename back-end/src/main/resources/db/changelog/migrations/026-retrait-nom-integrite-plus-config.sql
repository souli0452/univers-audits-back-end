--liquibase formatted sql
--changeset dev:026-retrait-nom-integrite-plus-config

-- Le nom « INTÉGRITÉ+ » et le logo associé ne sont plus utilisés : l'identité du portail est celle de l'ASCE-LC.
-- On corrige les textes de configuration semés en 005 (affichés dans Administration > Paramètres du portail),
-- ainsi que le libellé « Récépissé B4 » des modèles de notification (le mot « B4 » n'apparaît plus à l'écran).
-- La migration 005 n'est pas modifiée : un changeset déjà appliqué ne doit pas changer (somme de contrôle).
UPDATE portal_config
SET config_value = 'ASCE-LC'
WHERE config_key = 'site_name'
  AND config_value = 'INTÉGRITÉ+';

-- Le fichier /assets/logo-integrite.png n'existe pas : la clé pointe désormais vers le logo ASCE-LC.
UPDATE portal_config
SET config_value = '/assets/logo-asce.png',
    description  = 'Logo ASCE-LC (navbar, footer)'
WHERE config_key = 'logo_integrite'
  AND config_value = '/assets/logo-integrite.png';

UPDATE portal_config
SET label = 'Récépissé de dépôt — sujet'
WHERE config_key = 'notif_subject_receipt_b4'
  AND label = 'Récépissé B4 — sujet';

UPDATE portal_config
SET label = 'Récépissé de dépôt — contenu'
WHERE config_key = 'notif_content_receipt_b4'
  AND label = 'Récépissé B4 — contenu';
