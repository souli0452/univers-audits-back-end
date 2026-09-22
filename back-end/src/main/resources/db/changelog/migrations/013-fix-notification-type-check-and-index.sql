--liquibase formatted sql
--changeset dev:013-fix-notification-type-check-and-index

-- La regeneration du schema de reference (001-baseline-schema.sql, 2026-09-17)
-- a fige le CHECK constraint notification_type_check avec la liste des 11
-- valeurs de NotificationType existant a cette date. Le sous-chantier
-- "alertes-delai-j3" a ajoute 5 nouvelles valeurs a l'enum Java sans elargir
-- ce CHECK constraint SQL en parallele -- gap trouve par la revue finale de
-- branche, confirme empiriquement (INSERT direct sur la base migree rejette
-- les 5 nouvelles valeurs, ce qui aurait fait echouer et annuler
-- sendDeadlineAlerts() des le premier jour ou une echeance entre en fenetre
-- J-3). ddl-auto=validate ne verifie jamais les CHECK constraints, donc rien
-- dans les tests ne detectait ce gap avant cette revue.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN (
        'RECEIPT_B4','ACKNOWLEDGMENT_B5','COMPLEMENT_REQUEST',
        'INADMISSIBILITY_DECISION','TRANSFER_DECISION','FINAL_DECISION',
        'DEADLINE_ALERT','INTERNAL_ALERT','STATUS_UPDATE',
        'INVESTIGATION_ALERT','INVESTIGATION_ASSIGNMENT',
        'DEADLINE_ALERT_J3','COMPLEMENT_ALERT_J3','INVESTIGATION_ALERT_J3',
        'DEMANDE_DOCUMENTS_ALERT','DEMANDE_DOCUMENTS_ALERT_J3'));

-- Index manquant sur la FK ajoutee par 011 -- Postgres n'indexe jamais
-- automatiquement une colonne de cle etrangere. existsByDemandeDocumentsIdAndType*
-- scannerait sinon toute la table notification, qui croit de facon monotone
-- (jamais purgee).
CREATE INDEX idx_notification_demande_documents ON notification (demande_documents_id);
