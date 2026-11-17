--liquibase formatted sql
--changeset dev:035-saisine-categories-region-typologie

-- Situation trimestrielle publiée sur le site (document « Données pour publication ») :
--  * deux catégories de saisine de plus, qualifiées par l'agent qui enregistre ;
--  * Messenger distinct de WhatsApp ;
--  * origine géographique et typologie institutionnelle de l'entité visée.
ALTER TABLE dossier DROP CONSTRAINT dossier_type_check;
ALTER TABLE dossier ADD CONSTRAINT dossier_type_check CHECK (type IN
    ('DENONCIATION', 'PLAINTE', 'SIGNALEMENT', 'AUTO_SAISINE', 'PLAINTE_DENONCIATION', 'DEMANDE_INTERVENTION'));

ALTER TABLE dossier DROP CONSTRAINT dossier_social_platform_check;
ALTER TABLE dossier ADD CONSTRAINT dossier_social_platform_check CHECK (social_platform IN
    ('FACEBOOK', 'WHATSAPP', 'MESSENGER', 'TWITTER_X', 'YOUTUBE', 'OTHER'));

ALTER TABLE dossier ADD COLUMN region_origine VARCHAR(25);
ALTER TABLE dossier ADD COLUMN typologie_institutionnelle VARCHAR(40);

ALTER TABLE dossier ADD CONSTRAINT dossier_region_origine_check CHECK (region_origine IS NULL OR region_origine IN
    ('KADIOGO', 'GUIRIKO', 'AUTRES_REGIONS', 'DIASPORA'));
ALTER TABLE dossier ADD CONSTRAINT dossier_typologie_institutionnelle_check CHECK (typologie_institutionnelle IS NULL
    OR typologie_institutionnelle IN ('MINISTERES_ADMINISTRATIONS_CENTRALES', 'ETABLISSEMENTS_PUBLICS',
    'SOCIETES_D_ETAT', 'PROJETS_PROGRAMMES', 'FONDS_FINANCEMENT', 'COLLECTIVITES_TERRITORIALES', 'AUTRES'));
