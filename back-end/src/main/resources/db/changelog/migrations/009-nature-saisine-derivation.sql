--liquibase formatted sql
--changeset dev:009-nature-saisine-derivation

ALTER TABLE dossier
    ADD COLUMN IF NOT EXISTS quality VARCHAR(30);

COMMENT ON COLUMN dossier.quality IS 'Qualite du deposant (VICTIME / REPRESENTANT_VICTIME / TEMOIN) - nulle pour SIGNALEMENT et AUTO_SAISINE';

-- Remap des valeurs existantes de dossier.type vers la nouvelle nomenclature
-- (TypeSaisine{COMPLAINT,DENUNCIATION,AUTO_REFERRAL,ANONYMOUS} -> {DENONCIATION,PLAINTE,SIGNALEMENT,AUTO_SAISINE}).
-- dossier.quality reste NULL sur les lignes existantes : l'ancienne colonne
-- declarant.quality etait une chaine libre non validee, sa fiabilite n'est
-- pas garantie pour reconstruire automatiquement la qualite du deposant.
UPDATE dossier SET type = 'PLAINTE'      WHERE type = 'COMPLAINT';
UPDATE dossier SET type = 'DENONCIATION' WHERE type = 'DENUNCIATION';
UPDATE dossier SET type = 'AUTO_SAISINE' WHERE type = 'AUTO_REFERRAL';

-- Les dossiers historiques marques ANONYMOUS deviennent des denonciations ; on
-- force la coherence du declarant lie plutot que de supposer qu'elle etait deja correcte.
UPDATE declarant SET anonymous = TRUE
    WHERE id IN (
        SELECT declarant_id FROM dossier
        WHERE type = 'ANONYMOUS' AND declarant_id IS NOT NULL
    );
UPDATE dossier SET type = 'DENONCIATION' WHERE type = 'ANONYMOUS';

ALTER TABLE declarant
    DROP COLUMN IF EXISTS quality;
