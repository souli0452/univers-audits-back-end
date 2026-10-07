--liquibase formatted sql
--changeset dev:024-retrait-reference-loi-2004-notifications

-- La loi N°010-2004/AN porte sur les données personnelles (remplacée par la loi N°001-2021/AN) : elle ne
-- fonde pas la protection des lanceurs d'alerte. On retire la référence des deux modèles de notification
-- semés en 005 (le texte de loi à citer pour cette protection reste à confirmer par l'ASCE-LC).
-- La migration 005 n'est pas modifiée : un changeset déjà appliqué ne doit pas changer (somme de contrôle).
UPDATE portal_config
SET config_value = replace(config_value, ' (Loi N°010-2004/AN)', '')
WHERE config_key IN ('notif_content_protection_submitted', 'notif_content_protection_registered')
  AND config_value LIKE '%(Loi N°010-2004/AN)%';
