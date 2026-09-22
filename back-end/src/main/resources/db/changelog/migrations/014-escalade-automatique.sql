--liquibase formatted sql
--changeset dev:014-escalade-automatique

-- 4 nouveaux NotificationType pour l'escalade automatique vers CGEA/CGE.
-- Elargissement du CHECK constraint DANS LE MEME changeset que l'ajout des
-- valeurs -- lecon du Critical trouve par la revue finale du sous-chantier
-- precedent (alertes-delai-j3, migration 013) : un ajout de valeur d'enum
-- sans elargissement synchrone du CHECK constraint SQL fait echouer et
-- annuler toute transaction qui tente de l'utiliser.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN (
        'RECEIPT_B4','ACKNOWLEDGMENT_B5','COMPLEMENT_REQUEST',
        'INADMISSIBILITY_DECISION','TRANSFER_DECISION','FINAL_DECISION',
        'DEADLINE_ALERT','INTERNAL_ALERT','STATUS_UPDATE',
        'INVESTIGATION_ALERT','INVESTIGATION_ASSIGNMENT',
        'DEADLINE_ALERT_J3','COMPLEMENT_ALERT_J3','INVESTIGATION_ALERT_J3',
        'DEMANDE_DOCUMENTS_ALERT','DEMANDE_DOCUMENTS_ALERT_J3',
        'ESCALADE_AR','ESCALADE_COMPLEMENT','ESCALADE_INVESTIGATION',
        'ESCALADE_DEMANDE_DOCUMENTS'));

-- Delai de grace avant escalade (jours calendaires entre le depassement
-- d'une echeance et la notification automatique a CGEA/CGE).
INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'ESCALADE_DELAI_GRACE', 'Délai de grâce avant escalade automatique vers CGEA/CGE', 3, FALSE, TRUE, 0, now());
