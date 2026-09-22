--liquibase formatted sql
--changeset dev:016-fiche-affectation

-- Nouvelle table pour la fiche speciale d'affectation des dossiers
-- (circuit BRPD -> Cabinet CGE -> CGEA -> departement/conseiller juridique
-- -> suivi). Voir docs/superpowers/specs/2026-09-22-fiche-affectation-design.md.
CREATE TABLE fiche_affectation (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    dossier_id                  uuid NOT NULL UNIQUE REFERENCES dossier(id),

    decision_cge                character varying(30),
    observations_cge            text,
    agent_cge_id                uuid REFERENCES agent(id),
    date_decision_cge           timestamp(6) with time zone,

    type_designation            character varying(20),
    departement_designe_id      uuid REFERENCES departement(id),
    agent_designe_id            uuid REFERENCES agent(id),
    observations_cgea           text,
    agent_cgea_id                uuid REFERENCES agent(id),
    date_imputation             timestamp(6) with time zone,

    date_retour                 timestamp(6) with time zone,
    etat_avancement             character varying(20),
    etat_avancement_precision   character varying(300),
    commentaires_suivi          text,
    agent_suivi_id               uuid REFERENCES agent(id),

    created_at                  timestamp(6) with time zone NOT NULL,
    updated_at                  timestamp(6) with time zone,
    created_by_id                character varying(100),
    updated_by_id                character varying(100),
    version                     bigint NOT NULL DEFAULT 0,

    CONSTRAINT fiche_affectation_decision_cge_check
        CHECK (decision_cge IN ('AFFECTATION_DIRECTE_CGEA','ECHANGE_PREALABLE')),
    CONSTRAINT fiche_affectation_type_designation_check
        CHECK (type_designation IN ('DEPARTEMENT','AGENT_CJ','BRPD')),
    CONSTRAINT fiche_affectation_etat_avancement_check
        CHECK (etat_avancement IN ('EN_COURS','CLOTURE','AUTRE'))
);

CREATE INDEX idx_fiche_affectation_dossier_id ON fiche_affectation(dossier_id);

-- Nouveau type de notification pour l'affectation d'un dossier (fan-out au
-- departement/agent/BRPD designe). Elargissement du CHECK constraint DANS LE
-- MEME changeset -- lecon deja documentee 2 fois dans ce depot (migrations
-- 013, 014) : ne jamais separer l'ajout d'une valeur d'enum NotificationType
-- de l'elargissement du CHECK constraint SQL correspondant.
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
        'ESCALADE_DEMANDE_DOCUMENTS','AFFECTATION_DOSSIER'));
