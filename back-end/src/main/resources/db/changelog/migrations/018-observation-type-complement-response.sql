--liquibase formatted sql
--changeset dev:018-observation-type-complement-response

-- Nouveau ObservationType COMPLEMENT_RESPONSE : réponse du déclarant à une demande de
-- complément, déposée depuis le portail public. Le CHECK est élargi DANS LE MEME changeset
-- que l'ajout de la valeur (leçon des migrations 013 et 014).
ALTER TABLE observation DROP CONSTRAINT observation_type_check;
ALTER TABLE observation ADD CONSTRAINT observation_type_check
    CHECK (type IN (
        'INTERNAL_NOTE','ADMISSIBILITY_ANALYSIS','CTADP_OPINION','COMPLEMENT_REQUEST',
        'CGE_DECISION','FIELD_FINDING','TRANSFER_NOTE','COMPLEMENT_RESPONSE'));
