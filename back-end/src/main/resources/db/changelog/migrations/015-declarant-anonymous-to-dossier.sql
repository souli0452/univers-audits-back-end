--liquibase formatted sql
--changeset dev:015-declarant-anonymous-to-dossier

ALTER TABLE dossier ADD COLUMN IF NOT EXISTS anonymous BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dossier.anonymous IS 'Demande d''anonymat du declarant pour CETTE saisine uniquement — ne doit pas etre deduite de declarant.anonymous (personne reutilisable entre dossiers)';

-- Report l'anonymat historique du declarant vers chacun de ses dossiers, avant de
-- supprimer la colonne : un declarant marque anonymous=true l'etait potentiellement
-- sur tous ses dossiers existants (aucune granularite par dossier avant cette migration).
UPDATE dossier d SET anonymous = TRUE
    FROM declarant de
    WHERE d.declarant_id = de.id AND de.anonymous = TRUE;

ALTER TABLE declarant DROP COLUMN IF EXISTS anonymous;
