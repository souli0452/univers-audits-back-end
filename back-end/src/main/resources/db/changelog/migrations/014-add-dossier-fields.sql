--liquibase formatted sql
--changeset dev:014-add-dossier-fields

ALTER TABLE dossier
    ADD COLUMN IF NOT EXISTS lieu_depot                   VARCHAR(300),
    ADD COLUMN IF NOT EXISTS organisme_faits_denomination VARCHAR(200),
    ADD COLUMN IF NOT EXISTS organisme_faits_adresse      VARCHAR(300),
    ADD COLUMN IF NOT EXISTS attentes                     TEXT,
    ADD COLUMN IF NOT EXISTS decision_justice_existante   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS decision_justice_precision   TEXT,
    ADD COLUMN IF NOT EXISTS autre_institution_saisie     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS autre_institution_nom        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS autre_institution_adresse    VARCHAR(300);

COMMENT ON COLUMN dossier.lieu_depot IS 'Lieu de la denonciation/plainte (lieu de depot) - distinct de incident_location (lieu des faits)';
COMMENT ON COLUMN dossier.organisme_faits_denomination IS 'Organisme ou les faits allegues ont ete perpetres - distinct de la partie visee (targeted_party)';
COMMENT ON COLUMN dossier.attentes IS 'Ce que le deposant attend que l''ASCE-LC fasse - distinct d''object (titre court)';
COMMENT ON COLUMN dossier.decision_justice_existante IS 'Decision de justice deja rendue ou instance en cours - controle de recevabilite (litispendance)';
COMMENT ON COLUMN dossier.autre_institution_saisie IS 'Autre institution deja saisie des memes faits - controle de recevabilite (competence)';
