--liquibase formatted sql
--changeset dev:019-portal-config-hotline-numero-vert

-- Le numéro vert institutionnel de l'ASCE-LC est 80 00 11 02 (constante AsceLcInstitutionalInfo.NUMERO_VERT,
-- utilisée dans les récépissés et les PDF). Le portail public, lui, lit le paramètre « hotline_number », que la
-- migration 005 avait initialisé à l'ancien numéro « 80 00 11 11 ».
--
-- Mise à jour CONDITIONNELLE : si l'administration a déjà saisi un autre numéro dans « Paramètres du portail »,
-- la ligne ne correspond plus à la condition et n'est pas écrasée. La version est incrémentée pour rester
-- cohérente avec le verrouillage optimiste de l'entité PortalConfig.
UPDATE portal_config
   SET config_value = '80 00 11 02',
       updated_at   = now(),
       version      = COALESCE(version, 0) + 1
 WHERE config_key   = 'hotline_number'
   AND config_value = '80 00 11 11';
