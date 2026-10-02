--liquibase formatted sql
--changeset dev:023-alerte-delai-etape

-- Alerte automatique vers CGEA/CGE quand une étape du circuit de traitement dépasse son échéance
-- (workflow PGPD_GU V3). Une seule alerte par dossier et par étape : etape_code sert à la dédoublonner.
ALTER TABLE notification ADD COLUMN etape_code character varying(40);

CREATE INDEX idx_notification_dossier_etape ON notification (case_id, etape_code);

-- Ajout de la valeur d'enum NotificationType ET élargissement du CHECK dans le MEME changeset
-- (leçon des migrations 013, 014 et 016 : sinon toute transaction qui l'utilise est annulée).
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
        'ESCALADE_DEMANDE_DOCUMENTS','AFFECTATION_DOSSIER','ALERTE_DELAI_ETAPE'));

-- Modèles de texte (modifiables dans « Paramètres du portail »). PortalConfig n'étend pas AuditEntity :
-- id/updated_at/version doivent être fournis explicitement.
INSERT INTO portal_config
    (id, config_key, config_value, label, description, value_type, group_name, updated_at, version)
VALUES
(gen_random_uuid(), 'notif_subject_alerte_delai_etape',
 'DÉLAI DÉPASSÉ : {etape} — {numero}',
 'Alerte délai d''étape — sujet',
 'Alerte automatique vers CGEA/CGE quand une étape du circuit dépasse son échéance. Variables : {numero}, {etape}, {acteur}, {echeance}, {retard}.',
 'TEXT', 'NOTIFICATIONS', now(), 0),
(gen_random_uuid(), 'notif_content_alerte_delai_etape',
 'L''étape « {etape} » du dossier {numero}, confiée à {acteur}, devait être terminée le {echeance}. Elle est en retard de {retard}.',
 'Alerte délai d''étape — contenu',
 'Alerte automatique vers CGEA/CGE quand une étape du circuit dépasse son échéance. Variables : {numero}, {etape}, {acteur}, {echeance}, {retard}.',
 'TEXT', 'NOTIFICATIONS', now(), 0)
ON CONFLICT (config_key) DO NOTHING;

--rollback DELETE FROM portal_config WHERE config_key IN ('notif_subject_alerte_delai_etape','notif_content_alerte_delai_etape');
--rollback DELETE FROM notification WHERE type = 'ALERTE_DELAI_ETAPE';
--rollback ALTER TABLE notification DROP CONSTRAINT notification_type_check;
--rollback ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN ('RECEIPT_B4','ACKNOWLEDGMENT_B5','COMPLEMENT_REQUEST','INADMISSIBILITY_DECISION','TRANSFER_DECISION','FINAL_DECISION','DEADLINE_ALERT','INTERNAL_ALERT','STATUS_UPDATE','INVESTIGATION_ALERT','INVESTIGATION_ASSIGNMENT','DEADLINE_ALERT_J3','COMPLEMENT_ALERT_J3','INVESTIGATION_ALERT_J3','DEMANDE_DOCUMENTS_ALERT','DEMANDE_DOCUMENTS_ALERT_J3','ESCALADE_AR','ESCALADE_COMPLEMENT','ESCALADE_INVESTIGATION','ESCALADE_DEMANDE_DOCUMENTS','AFFECTATION_DOSSIER'));
--rollback DROP INDEX idx_notification_dossier_etape;
--rollback ALTER TABLE notification DROP COLUMN etape_code;
