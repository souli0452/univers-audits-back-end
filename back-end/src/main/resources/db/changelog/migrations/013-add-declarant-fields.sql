--liquibase formatted sql
--changeset dev:013-add-declarant-fields

ALTER TABLE declarant
    ADD COLUMN IF NOT EXISTS cellulaire VARCHAR(20),
    ADD COLUMN IF NOT EXISTS localite   VARCHAR(100);

COMMENT ON COLUMN declarant.cellulaire IS 'Numero de telephone cellulaire, distinct du telephone fixe (phone_number)';
COMMENT ON COLUMN declarant.localite   IS 'Localite (quartier/village), plus fine que commune/province';
