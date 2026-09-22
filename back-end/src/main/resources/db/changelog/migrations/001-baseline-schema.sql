--liquibase formatted sql
--changeset dev:001-baseline-schema

-- Schema de reference (baseline) genere depuis les entites JPA actuelles via
-- Hibernate ddl-auto=create le 2026-09-17, puis pg_dump --schema-only.
--
-- Pourquoi ce fichier existe : les migrations 001-042 qui existaient avant
-- (et 4 fichiers de changelog explicites) n'etaient que des modifications
-- incrementales sur un schema jamais capture nulle part - il avait ete
-- construit par Hibernate (ddl-auto=update) entre le premier commit du
-- projet (2026-03-09) et le passage a ddl-auto=validate + Liquibase
-- (commit 4ecbc3f, 2026-05-07), sans jamais etre exporte. Consequence :
-- ce depot, seul, ne pouvait pas amorcer une base de donnees vierge
-- (confirme par test reel : "relation dossier does not exist" des la
-- premiere migration rejouee sur un Postgres neuf).
--
-- Ce fichier remplace l'integralite de l'ancien historique de migrations
-- (desormais supprime) par un etat unique et complet, correspondant
-- exactement a ce que les entites JPA actuelles attendent
-- (spring.jpa.hibernate.ddl-auto=validate). Toute nouvelle migration future
-- doit etre numerotee a partir de 002.

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.13 (Debian 16.13-1.pgdg13+1)
-- Dumped by pg_dump version 16.13 (Debian 16.13-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent (
    actif boolean NOT NULL,
    date_prise_fonction date,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    departement_id uuid,
    id uuid NOT NULL,
    matricule character varying(20) NOT NULL,
    phone_number character varying(20),
    keycloak_id character varying(36),
    created_by_id character varying(100),
    first_name character varying(100) NOT NULL,
    grade character varying(100),
    last_name character varying(100) NOT NULL,
    updated_by_id character varying(100),
    email character varying(150) NOT NULL
);


--
-- Name: attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attachment (
    direct_capture boolean,
    latitude double precision,
    longitude double precision,
    created_at timestamp(6) with time zone NOT NULL,
    file_size_bytes bigint NOT NULL,
    updated_at timestamp(6) with time zone,
    uploaded_at timestamp(6) without time zone NOT NULL,
    validated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    case_id uuid,
    id uuid NOT NULL,
    investigation_id uuid,
    section_id uuid,
    uploaded_by_id uuid,
    validated_by_id uuid,
    code character varying(20),
    mode_obtention character varying(20) NOT NULL,
    type character varying(20) NOT NULL,
    source character varying(25) NOT NULL,
    status character varying(25) NOT NULL,
    hash_sha256 character varying(64),
    created_by_id character varying(100),
    mime_type character varying(100) NOT NULL,
    updated_by_id character varying(100),
    file_path character varying(500) NOT NULL,
    thumbnail_path character varying(500),
    description text,
    original_name character varying(255) NOT NULL,
    personne_remettante character varying(255),
    rejection_reason text,
    stored_name character varying(255) NOT NULL,
    CONSTRAINT attachment_mode_obtention_check CHECK (((mode_obtention)::text = ANY ((ARRAY['VOLONTAIRE'::character varying, 'REQUISITION'::character varying])::text[]))),
    CONSTRAINT attachment_source_check CHECK (((source)::text = ANY ((ARRAY['INITIAL_SUBMISSION'::character varying, 'FIELD_INVESTIGATION'::character varying, 'SOCIAL_MEDIA'::character varying, 'PRESS_MEDIA'::character varying, 'EXTERNAL_AUDIT'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT attachment_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_VALIDATION'::character varying, 'VALIDATED'::character varying, 'REJECTED'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT attachment_type_check CHECK (((type)::text = ANY ((ARRAY['PHOTO'::character varying, 'DOCUMENT'::character varying, 'VIDEO'::character varying, 'AUDIO_EVIDENCE'::character varying, 'SCREENSHOT'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    status character varying(20) NOT NULL,
    ip_address character varying(45),
    entity_type character varying(60),
    action character varying(80) NOT NULL,
    agent_id character varying(100) NOT NULL,
    agent_role character varying(100),
    entity_id character varying(100),
    agent_name character varying(200),
    user_agent character varying(300),
    description character varying(500)
);


--
-- Name: audition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audition (
    conducted_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    scheduled_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    targeted_party_id uuid,
    witness_id uuid,
    interviewee_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    location character varying(300),
    cancellation_reason text,
    no_show_note text,
    summary text,
    CONSTRAINT audition_interviewee_type_check CHECK (((interviewee_type)::text = ANY ((ARRAY['TARGETED_PARTY'::character varying, 'WITNESS'::character varying, 'DECLARANT'::character varying])::text[]))),
    CONSTRAINT audition_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'CONDUCTED'::character varying, 'CANCELLED'::character varying, 'NO_SHOW'::character varying])::text[])))
);


--
-- Name: audition_investigator; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audition_investigator (
    agent_id uuid NOT NULL,
    audition_id uuid NOT NULL
);


--
-- Name: checklist_dossier_travail_coche; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.checklist_dossier_travail_coche (
    coche boolean NOT NULL,
    coche_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    coche_par_id uuid,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    point_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    commentaire character varying(2000)
);


--
-- Name: constitution_partie_civile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.constitution_partie_civile (
    montant_reclame numeric(15,2),
    constitue_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    constituee_par_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    justification text NOT NULL
);


--
-- Name: correction_pv_audition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.correction_pv_audition (
    version_number integer NOT NULL,
    corrected_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    corrected_by_id uuid NOT NULL,
    id uuid NOT NULL,
    pv_audition_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    content text NOT NULL,
    motif_correction text NOT NULL
);


--
-- Name: decision_cge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.decision_cge (
    created_at timestamp(6) with time zone NOT NULL,
    date_decision timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_cge_id uuid NOT NULL,
    dossier_id uuid NOT NULL,
    id uuid NOT NULL,
    decision character varying(40) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    motif character varying(2000),
    CONSTRAINT decision_cge_decision_check CHECK (((decision)::text = ANY ((ARRAY['VALIDATION_INVESTIGATION'::character varying, 'CLASSEMENT'::character varying, 'TRANSMISSION_INSTITUTION_PARTENAIRE'::character varying, 'ORIENTATION_ADMINISTRATIVE'::character varying])::text[])))
);


--
-- Name: declarant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.declarant (
    data_processing_consent boolean NOT NULL,
    notifications_accepted boolean NOT NULL,
    protection_requested boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    cellulaire character varying(20),
    phone_number character varying(20),
    id_document_type character varying(30),
    type_declarant character varying(30) NOT NULL,
    id_document_number character varying(50),
    commune character varying(100),
    created_by_id character varying(100),
    first_name character varying(100),
    last_name character varying(100),
    localite character varying(100),
    profession character varying(100),
    province character varying(100),
    updated_by_id character varying(100),
    email character varying(150),
    organization_name character varying(200),
    social_media_account character varying(200),
    address character varying(300),
    CONSTRAINT declarant_type_declarant_check CHECK (((type_declarant)::text = ANY ((ARRAY['CITIZEN'::character varying, 'COMPANY'::character varying, 'ASSOCIATION'::character varying, 'PUBLIC_AUTHORITY'::character varying, 'ANONYMOUS'::character varying, 'ASCE_SELF_REFERRAL'::character varying])::text[])))
);


--
-- Name: demande_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.demande_documents (
    received boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deadline timestamp(6) with time zone NOT NULL,
    received_at timestamp(6) with time zone,
    sent_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    requested_by_id uuid NOT NULL,
    escalation_level character varying(20) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    recipient_label character varying(300) NOT NULL,
    documents_requested text NOT NULL,
    CONSTRAINT demande_documents_escalation_level_check CHECK (((escalation_level)::text = ANY ((ARRAY['INITIAL'::character varying, 'RELANCE'::character varying, 'SOMMATION'::character varying, 'SAISINE_JUDICIAIRE'::character varying])::text[])))
);


--
-- Name: departement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departement (
    actif boolean NOT NULL,
    ordre_affichage integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    code character varying(20) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    libelle character varying(200) NOT NULL,
    description text
);


--
-- Name: dossier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dossier (
    anonymous boolean NOT NULL,
    autre_institution_saisie boolean NOT NULL,
    decision_justice_existante boolean NOT NULL,
    estimated_loss numeric(15,2),
    is_confidential boolean NOT NULL,
    acknowledgment_deadline timestamp(6) with time zone,
    additional_info_deadline timestamp(6) with time zone,
    closing_date timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    eligibility_decision_date timestamp(6) with time zone,
    priority_deadline timestamp(6) with time zone,
    priority_set_at timestamp(6) with time zone,
    reception_date timestamp(6) with time zone,
    transfer_date timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    access_code character varying(10) NOT NULL,
    agent_in_charge_id uuid,
    declarant_id uuid,
    id uuid NOT NULL,
    priority_set_by_id uuid,
    number character varying(20),
    priority character varying(20),
    social_platform character varying(20),
    type character varying(20) NOT NULL,
    organisation_detail character varying(25),
    submission_mode character varying(25),
    auto_referral_source character varying(30),
    description_source character varying(30),
    quality character varying(30),
    status character varying(35) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    autre_institution_nom character varying(200),
    incident_period character varying(200),
    organisme_faits_denomination character varying(200),
    autre_institution_adresse character varying(300),
    incident_location character varying(300),
    lieu_depot character varying(300),
    organisme_faits_adresse character varying(300),
    transfer_institution character varying(300),
    object character varying(500) NOT NULL,
    priority_reason character varying(500),
    source_reference character varying(500),
    attentes text,
    decision_justice_precision text,
    description text,
    motifs text,
    CONSTRAINT dossier_auto_referral_source_check CHECK (((auto_referral_source)::text = ANY ((ARRAY['WRITTEN_PRESS'::character varying, 'TELEVISION'::character varying, 'RADIO'::character varying, 'SOCIAL_MEDIA'::character varying, 'AUDIT_REPORT'::character varying, 'INSPECTION_REPORT'::character varying, 'INTERNAL_TIP'::character varying, 'PARTNER_INSTITUTION'::character varying, 'PROSECUTOR_REFERRAL'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT dossier_organisation_detail_check CHECK (((organisation_detail)::text = ANY ((ARRAY['PAR_ETAPE'::character varying, 'PAR_ENTITE'::character varying, 'PAR_SITE'::character varying, 'PAR_CYCLE_COMPTABLE'::character varying])::text[]))),
    CONSTRAINT dossier_priority_check CHECK (((priority)::text = ANY ((ARRAY['CRITIQUE'::character varying, 'URGENT'::character varying, 'NORMAL'::character varying, 'FAIBLE'::character varying])::text[]))),
    CONSTRAINT dossier_quality_check CHECK (((quality)::text = ANY ((ARRAY['VICTIME'::character varying, 'REPRESENTANT_VICTIME'::character varying, 'TEMOIN'::character varying])::text[]))),
    CONSTRAINT dossier_social_platform_check CHECK (((social_platform)::text = ANY ((ARRAY['FACEBOOK'::character varying, 'WHATSAPP'::character varying, 'TWITTER_X'::character varying, 'YOUTUBE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT dossier_status_check CHECK (((status)::text = ANY ((ARRAY['SOUMIS'::character varying, 'RECU'::character varying, 'EN_ETUDE_OPPORTUNITE'::character varying, 'EN_ATTENTE_COMPLEMENT'::character varying, 'EN_REVUE_CTADP'::character varying, 'RECEVABLE'::character varying, 'IRRECEVABLE'::character varying, 'TRANSFERE'::character varying, 'ORIENTEE_ADMINISTRATIF'::character varying, 'EN_INVESTIGATION'::character varying, 'RAPPORT_PRODUIT'::character varying, 'DECISION_RENDUE'::character varying, 'CLOS'::character varying, 'CLASSE'::character varying])::text[]))),
    CONSTRAINT dossier_submission_mode_check CHECK (((submission_mode)::text = ANY ((ARRAY['IN_PERSON'::character varying, 'AUDIO_COUNTER'::character varying, 'WEB_FORM'::character varying, 'PAPER_FORM'::character varying, 'EMAIL'::character varying, 'SMS'::character varying, 'PHONE'::character varying, 'FAX'::character varying, 'GREEN_NUMBER'::character varying, 'SOCIAL_MEDIA'::character varying, 'PRESS_MEDIA'::character varying, 'AUDIT_REPORT'::character varying, 'POSTAL_MAIL'::character varying])::text[]))),
    CONSTRAINT dossier_type_check CHECK (((type)::text = ANY ((ARRAY['DENONCIATION'::character varying, 'PLAINTE'::character varying, 'SIGNALEMENT'::character varying, 'AUTO_SAISINE'::character varying])::text[])))
);


--
-- Name: dossier_habilitation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dossier_habilitation (
    created_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_id uuid NOT NULL,
    dossier_id uuid NOT NULL,
    granted_by_id uuid,
    id uuid NOT NULL,
    revoked_by_id uuid,
    source character varying(25) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    reason character varying(500),
    revocation_reason character varying(500),
    CONSTRAINT dossier_habilitation_source_check CHECK (((source)::text = ANY ((ARRAY['AGENT_IN_CHARGE'::character varying, 'INVESTIGATION_TEAM'::character varying, 'MANUAL'::character varying])::text[])))
);


--
-- Name: engagement_confidentialite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.engagement_confidentialite (
    has_conflict_of_interest boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    signed_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    conflict_details character varying(2000)
);


--
-- Name: etude_opportunite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etude_opportunite (
    competence_asce_lc boolean,
    enquete_complementaire_necessaire boolean,
    opportunite_saisir_procureur boolean,
    preoccupation_reelle boolean,
    preuves_suffisantes boolean,
    secteur_sensible boolean,
    solidite_allegation boolean,
    urgence_securisation_preuves boolean,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    dossier_id uuid NOT NULL,
    id uuid NOT NULL,
    type_infraction_id uuid,
    nature_qualification character varying(20),
    qualification_non_penale character varying(30),
    created_by_id character varying(100),
    updated_by_id character varying(100),
    secteur_precision character varying(300),
    competence_asce_lc_commentaire character varying(2000),
    enquete_complementaire_necessaire_commentaire character varying(2000),
    opportunite_saisir_procureur_commentaire character varying(2000),
    preoccupation_reelle_commentaire character varying(2000),
    preuves_suffisantes_commentaire character varying(2000),
    solidite_allegation_commentaire character varying(2000),
    urgence_securisation_preuves_commentaire character varying(2000),
    avis_general character varying(5000),
    CONSTRAINT etude_opportunite_nature_qualification_check CHECK (((nature_qualification)::text = ANY ((ARRAY['PENALE'::character varying, 'ADMINISTRATIVE'::character varying])::text[]))),
    CONSTRAINT etude_opportunite_qualification_non_penale_check CHECK (((qualification_non_penale)::text = ANY ((ARRAY['IRREGULARITE'::character varying, 'FRAUDE'::character varying, 'ACTE_COLLUSION'::character varying, 'ACTES_ILLICITES'::character varying])::text[])))
);


--
-- Name: fiche_retex; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fiche_retex (
    impact_financier numeric(15,2),
    jours_charges integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    redige_par_id uuid NOT NULL,
    type_infraction_id uuid,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    lieu character varying(300),
    collaborateurs_planifies text,
    contexte text,
    difficultes_rencontrees text,
    enseignements_axes_amelioration text NOT NULL,
    originalite_schemas text,
    origine_soupcons text,
    strategie_methodes text,
    synthese_resultats text NOT NULL
);


--
-- Name: incident_objectivite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.incident_objectivite (
    created_at timestamp(6) with time zone NOT NULL,
    declared_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    declared_by_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    description text NOT NULL
);


--
-- Name: indice_fraude; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.indice_fraude (
    actif boolean NOT NULL,
    ordre integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    categorie character varying(100),
    created_by_id character varying(100),
    updated_by_id character varying(100),
    libelle character varying(300) NOT NULL,
    description text
);


--
-- Name: information_preoccupante; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.information_preoccupante (
    created_at timestamp(6) with time zone NOT NULL,
    date_reception timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    statut character varying(30) NOT NULL,
    source character varying(40) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    objet character varying(500) NOT NULL,
    source_reference character varying(500),
    description text NOT NULL,
    CONSTRAINT information_preoccupante_source_check CHECK (((source)::text = ANY ((ARRAY['WRITTEN_PRESS'::character varying, 'TELEVISION'::character varying, 'RADIO'::character varying, 'SOCIAL_MEDIA'::character varying, 'AUDIT_REPORT'::character varying, 'INSPECTION_REPORT'::character varying, 'INTERNAL_TIP'::character varying, 'PARTNER_INSTITUTION'::character varying, 'PROSECUTOR_REFERRAL'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT information_preoccupante_statut_check CHECK (((statut)::text = ANY ((ARRAY['NOUVELLE'::character varying, 'RATTACHEE'::character varying, 'AUTO_SAISINE_DECLENCHEE'::character varying, 'CLASSEE_SANS_SUITE'::character varying])::text[])))
);


--
-- Name: information_preoccupante_dossier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.information_preoccupante_dossier (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    dossier_id uuid NOT NULL,
    id uuid NOT NULL,
    information_preoccupante_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    commentaire character varying(2000)
);


--
-- Name: investigation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investigation (
    planned_duration_days integer NOT NULL,
    actual_end_date timestamp(6) with time zone,
    cge_approved_at timestamp(6) with time zone,
    cgea_approved_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    dei_approved_at timestamp(6) with time zone,
    extended_deadline timestamp(6) with time zone,
    legal_advisor_approved_at timestamp(6) with time zone,
    planned_end_date timestamp(6) with time zone,
    report_submitted_at timestamp(6) with time zone,
    start_date timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    case_id uuid NOT NULL,
    cge_approved_by_id uuid,
    cgea_approved_by_id uuid,
    cgea_id uuid NOT NULL,
    dei_approved_by_id uuid,
    extension_approved_by_id uuid,
    id uuid NOT NULL,
    legal_advisor_approved_by_id uuid,
    status character varying(20) NOT NULL,
    outcome character varying(30),
    created_by_id character varying(100),
    updated_by_id character varying(100),
    extension_reason text,
    suspension_reason text,
    CONSTRAINT investigation_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['ADMINISTRATIVE_SANCTIONS'::character varying, 'JUDICIAL_REFERRAL'::character varying, 'ARCHIVED'::character varying, 'PRESS_RELEASE'::character varying, 'ANNUAL_REPORT'::character varying])::text[]))),
    CONSTRAINT investigation_status_check CHECK (((status)::text = ANY ((ARRAY['INITIATED'::character varying, 'IN_PROGRESS'::character varying, 'SUSPENDED'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: investigation_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investigation_member (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    team_role character varying(20) NOT NULL,
    assigned_by character varying(100) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    CONSTRAINT investigation_member_team_role_check CHECK (((team_role)::text = ANY ((ARRAY['CHEF_MISSION'::character varying, 'INVESTIGATEUR'::character varying, 'PERSONNE_RESSOURCE'::character varying, 'CONSEIL_JURIDIQUE'::character varying])::text[])))
);


--
-- Name: lecon_a_partager; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lecon_a_partager (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    fiche_retex_id uuid NOT NULL,
    id uuid NOT NULL,
    publiee_par_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    titre character varying(300) NOT NULL,
    resume text NOT NULL
);


--
-- Name: login_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_log (
    success boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    ip_address character varying(45),
    agent_id character varying(100) NOT NULL,
    agent_name character varying(200),
    failure_reason character varying(200),
    user_agent character varying(300)
);


--
-- Name: mandat; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mandat (
    created_at timestamp(6) with time zone NOT NULL,
    date_delivrance timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_cge_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100)
);


--
-- Name: mesure_conservatoire; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mesure_conservatoire (
    created_at timestamp(6) with time zone NOT NULL,
    taken_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    taken_by_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    description text NOT NULL
);


--
-- Name: mission_suivi; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mission_suivi (
    created_at timestamp(6) with time zone NOT NULL,
    mission_date timestamp(6) with time zone NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    conducted_by_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    nouvelles_recommandations text,
    objectifs text NOT NULL,
    synthese_recommandations text NOT NULL
);


--
-- Name: note_avancement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.note_avancement (
    created_at timestamp(6) with time zone NOT NULL,
    note_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_id uuid NOT NULL,
    id uuid NOT NULL,
    plan_actions_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    contenu text
);


--
-- Name: note_recommandations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.note_recommandations (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    rapport_enquete_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    contenu text
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    retry_count integer NOT NULL,
    form_reference character varying(5),
    created_at timestamp(6) with time zone NOT NULL,
    read_at timestamp(6) with time zone,
    scheduled_at timestamp(6) with time zone,
    sent_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    channel character varying(15) NOT NULL,
    status character varying(15) NOT NULL,
    case_id uuid NOT NULL,
    id uuid NOT NULL,
    signed_by_id uuid,
    type character varying(35) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    recipient character varying(200),
    subject character varying(500),
    content text,
    error_message text,
    CONSTRAINT notification_channel_check CHECK (((channel)::text = ANY ((ARRAY['EMAIL'::character varying, 'SMS'::character varying, 'POSTAL_MAIL'::character varying, 'PORTAL'::character varying])::text[]))),
    CONSTRAINT notification_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT notification_type_check CHECK (((type)::text = ANY ((ARRAY['RECEIPT_B4'::character varying, 'ACKNOWLEDGMENT_B5'::character varying, 'COMPLEMENT_REQUEST'::character varying, 'INADMISSIBILITY_DECISION'::character varying, 'TRANSFER_DECISION'::character varying, 'FINAL_DECISION'::character varying, 'DEADLINE_ALERT'::character varying, 'INTERNAL_ALERT'::character varying, 'STATUS_UPDATE'::character varying, 'INVESTIGATION_ALERT'::character varying, 'INVESTIGATION_ASSIGNMENT'::character varying])::text[])))
);


--
-- Name: observation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.observation (
    confidential boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    author_id uuid NOT NULL,
    case_id uuid NOT NULL,
    id uuid NOT NULL,
    type character varying(30) NOT NULL,
    status_snapshot character varying(35),
    created_by_id character varying(100),
    cree_par character varying(100),
    updated_by_id character varying(100),
    author_full_name character varying(200) NOT NULL,
    content text NOT NULL,
    CONSTRAINT observation_status_snapshot_check CHECK (((status_snapshot)::text = ANY ((ARRAY['SOUMIS'::character varying, 'RECU'::character varying, 'EN_ETUDE_OPPORTUNITE'::character varying, 'EN_ATTENTE_COMPLEMENT'::character varying, 'EN_REVUE_CTADP'::character varying, 'RECEVABLE'::character varying, 'IRRECEVABLE'::character varying, 'TRANSFERE'::character varying, 'ORIENTEE_ADMINISTRATIF'::character varying, 'EN_INVESTIGATION'::character varying, 'RAPPORT_PRODUIT'::character varying, 'DECISION_RENDUE'::character varying, 'CLOS'::character varying, 'CLASSE'::character varying])::text[]))),
    CONSTRAINT observation_type_check CHECK (((type)::text = ANY ((ARRAY['INTERNAL_NOTE'::character varying, 'ADMISSIBILITY_ANALYSIS'::character varying, 'CTADP_OPINION'::character varying, 'COMPLEMENT_REQUEST'::character varying, 'CGE_DECISION'::character varying, 'FIELD_FINDING'::character varying, 'TRANSFER_NOTE'::character varying])::text[])))
);


--
-- Name: parametre_delai; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parametre_delai (
    actif boolean NOT NULL,
    jours_ouvrables boolean NOT NULL,
    valeur_jours integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    libelle character varying(300) NOT NULL
);


--
-- Name: permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    category character varying(60),
    permission_key character varying(80) NOT NULL,
    label character varying(200) NOT NULL,
    description character varying(500)
);


--
-- Name: plan_actions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_actions (
    created_at timestamp(6) with time zone NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    received_by_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    entite_controlee character varying(300) NOT NULL,
    contenu text NOT NULL
);


--
-- Name: plan_investigation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_investigation (
    plan_version integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    validated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    submitted_by_id uuid NOT NULL,
    validated_by_id uuid,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    methodologie text NOT NULL,
    moyens_mobilises text,
    objectifs text NOT NULL,
    planning_procedures text
);


--
-- Name: point_checklist_dossier_travail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.point_checklist_dossier_travail (
    actif boolean NOT NULL,
    ordre integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    categorie character varying(100),
    created_by_id character varying(100),
    updated_by_id character varying(100),
    libelle character varying(500) NOT NULL
);


--
-- Name: portal_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.portal_config (
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    id uuid NOT NULL,
    value_type character varying(20) NOT NULL,
    group_name character varying(60) NOT NULL,
    config_key character varying(80) NOT NULL,
    label character varying(200) NOT NULL,
    updated_by character varying(200),
    description character varying(500),
    config_value text
);


--
-- Name: procedure_urgence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.procedure_urgence (
    created_at timestamp(6) with time zone NOT NULL,
    decided_at timestamp(6) with time zone,
    requested_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    decided_by_id uuid,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    requested_by_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    justification text NOT NULL,
    motif_decision text,
    CONSTRAINT procedure_urgence_status_check CHECK (((status)::text = ANY ((ARRAY['EN_ATTENTE'::character varying, 'APPROUVEE'::character varying, 'REJETEE'::character varying])::text[])))
);


--
-- Name: pv_audition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pv_audition (
    interviewee_signature_refused boolean NOT NULL,
    interviewee_signed boolean NOT NULL,
    pv_version integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    finalized_at timestamp(6) with time zone,
    read_back_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    audition_id uuid NOT NULL,
    drafted_by_id uuid NOT NULL,
    id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    content text NOT NULL
);


--
-- Name: pv_constat; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pv_constat (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    drafted_by_id uuid NOT NULL,
    id uuid NOT NULL,
    visite_terrain_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    content text NOT NULL
);


--
-- Name: rapport_enquete; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rapport_enquete (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    conclusions text,
    expose_factuel_anomalies text,
    informations_collectees text,
    introduction text,
    methodologie text,
    quantification_prejudice text,
    reserves text,
    titre text
);


--
-- Name: relance_suites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.relance_suites (
    created_at timestamp(6) with time zone NOT NULL,
    relance_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_id uuid NOT NULL,
    id uuid NOT NULL,
    transmission_autorite_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    contenu text
);


--
-- Name: requete_parquet; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.requete_parquet (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    contenu text
);


--
-- Name: revision_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revision_plan (
    version_number integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    revised_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    plan_investigation_id uuid NOT NULL,
    revised_by_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    methodologie text NOT NULL,
    motif_revision text NOT NULL,
    moyens_mobilises text,
    objectifs text NOT NULL,
    planning_procedures text
);


--
-- Name: role_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_definition (
    active boolean NOT NULL,
    display_order integer NOT NULL,
    is_protected boolean NOT NULL,
    visible boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    id uuid NOT NULL,
    severity character varying(20) NOT NULL,
    icon character varying(60) NOT NULL,
    role_key character varying(60) NOT NULL,
    label character varying(150) NOT NULL,
    description character varying(500)
);


--
-- Name: role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permission (
    permission_id uuid NOT NULL,
    role_id uuid NOT NULL
);


--
-- Name: seance_ctadp; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seance_ctadp (
    created_at timestamp(6) with time zone NOT NULL,
    date_seance timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    statut character varying(20) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    participants character varying(2000),
    proces_verbal character varying(5000),
    CONSTRAINT seance_ctadp_statut_check CHECK (((statut)::text = ANY ((ARRAY['PLANIFIEE'::character varying, 'TENUE'::character varying, 'ANNULEE'::character varying])::text[])))
);


--
-- Name: seance_ctadp_dossier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seance_ctadp_dossier (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    dossier_id uuid NOT NULL,
    id uuid NOT NULL,
    seance_ctadp_id uuid NOT NULL,
    recommandation character varying(40),
    created_by_id character varying(100),
    updated_by_id character varying(100),
    commentaire character varying(2000),
    CONSTRAINT seance_ctadp_dossier_recommandation_check CHECK (((recommandation)::text = ANY ((ARRAY['VALIDATION_INVESTIGATION'::character varying, 'CLASSEMENT'::character varying, 'TRANSMISSION_INSTITUTION_PARTENAIRE'::character varying, 'ORIENTATION_ADMINISTRATIVE'::character varying])::text[])))
);


--
-- Name: section_dossier_travail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.section_dossier_travail (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    dossier_id uuid NOT NULL,
    id uuid NOT NULL,
    type character varying(35) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    libelle character varying(255),
    CONSTRAINT section_dossier_travail_type_check CHECK (((type)::text = ANY ((ARRAY['ADMINISTRATION_MISSION'::character varying, 'PRISE_CONNAISSANCE_ENTITE'::character varying, 'PRISE_CONNAISSANCE_ENVIRONNEMENT'::character varying, 'DETAIL'::character varying])::text[])))
);


--
-- Name: status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.status_history (
    changed_at timestamp(6) with time zone NOT NULL,
    agent_id uuid,
    case_id uuid NOT NULL,
    id uuid NOT NULL,
    new_status character varying(35) NOT NULL,
    previous_status character varying(35),
    ip_address character varying(45),
    agent_full_name character varying(200),
    reason text,
    CONSTRAINT status_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['SOUMIS'::character varying, 'RECU'::character varying, 'EN_ETUDE_OPPORTUNITE'::character varying, 'EN_ATTENTE_COMPLEMENT'::character varying, 'EN_REVUE_CTADP'::character varying, 'RECEVABLE'::character varying, 'IRRECEVABLE'::character varying, 'TRANSFERE'::character varying, 'ORIENTEE_ADMINISTRATIF'::character varying, 'EN_INVESTIGATION'::character varying, 'RAPPORT_PRODUIT'::character varying, 'DECISION_RENDUE'::character varying, 'CLOS'::character varying, 'CLASSE'::character varying])::text[]))),
    CONSTRAINT status_history_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['SOUMIS'::character varying, 'RECU'::character varying, 'EN_ETUDE_OPPORTUNITE'::character varying, 'EN_ATTENTE_COMPLEMENT'::character varying, 'EN_REVUE_CTADP'::character varying, 'RECEVABLE'::character varying, 'IRRECEVABLE'::character varying, 'TRANSFERE'::character varying, 'ORIENTEE_ADMINISTRATIF'::character varying, 'EN_INVESTIGATION'::character varying, 'RAPPORT_PRODUIT'::character varying, 'DECISION_RENDUE'::character varying, 'CLOS'::character varying, 'CLASSE'::character varying])::text[])))
);


--
-- Name: suivi_procedure_penale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suivi_procedure_penale (
    created_at timestamp(6) with time zone NOT NULL,
    phase_at timestamp(6) with time zone NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    agent_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    phase character varying(300) NOT NULL,
    commentaire text
);


--
-- Name: targeted_party; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.targeted_party (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    case_id uuid NOT NULL,
    id uuid NOT NULL,
    alleged_role character varying(20),
    phone_number character varying(20),
    party_type character varying(25) NOT NULL,
    created_by_id character varying(100),
    first_name character varying(100),
    name character varying(100),
    updated_by_id character varying(100),
    email character varying(150),
    "position" character varying(150),
    institution character varying(200),
    organization character varying(200),
    relation_with_declarant character varying(200),
    address character varying(300),
    CONSTRAINT targeted_party_alleged_role_check CHECK (((alleged_role)::text = ANY ((ARRAY['MAIN_PERPETRATOR'::character varying, 'ACCOMPLICE'::character varying, 'BENEFICIARY'::character varying, 'INSTIGATOR'::character varying])::text[]))),
    CONSTRAINT targeted_party_party_type_check CHECK (((party_type)::text = ANY ((ARRAY['PRIVATE_PERSON'::character varying, 'COMPANY'::character varying, 'PUBLIC_AGENT'::character varying, 'PUBLIC_AUTHORITY'::character varying])::text[])))
);


--
-- Name: transmission_autorite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transmission_autorite (
    created_at timestamp(6) with time zone NOT NULL,
    transmitted_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    transmitted_by_id uuid NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    autorite_destinataire character varying(300) NOT NULL
);


--
-- Name: type_declarant_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.type_declarant_config (
    active boolean NOT NULL,
    consent_required boolean NOT NULL,
    display_order integer,
    id_document_required boolean NOT NULL,
    protection_available boolean NOT NULL,
    visible_on_public_form boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    type_declarant character varying(30) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    label character varying(150) NOT NULL,
    description text,
    CONSTRAINT type_declarant_config_type_declarant_check CHECK (((type_declarant)::text = ANY ((ARRAY['CITIZEN'::character varying, 'COMPANY'::character varying, 'ASSOCIATION'::character varying, 'PUBLIC_AUTHORITY'::character varying, 'ANONYMOUS'::character varying, 'ASCE_SELF_REFERRAL'::character varying])::text[])))
);


--
-- Name: type_infraction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.type_infraction (
    actif boolean NOT NULL,
    implique_ddip boolean NOT NULL,
    ordre integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    article_code_penal character varying(100),
    article_loi_004 character varying(100),
    created_by_id character varying(100),
    updated_by_id character varying(100),
    libelle character varying(300) NOT NULL
);


--
-- Name: visite_terrain; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.visite_terrain (
    conducted_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    scheduled_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    conducted_by_id uuid NOT NULL,
    id uuid NOT NULL,
    investigation_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    created_by_id character varying(100),
    updated_by_id character varying(100),
    location character varying(300) NOT NULL,
    cancellation_reason text,
    carence_reason text,
    summary text,
    CONSTRAINT visite_terrain_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'CONDUCTED'::character varying, 'CANCELLED'::character varying, 'CARENCE'::character varying])::text[])))
);


--
-- Name: witness; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.witness (
    anonymous boolean NOT NULL,
    consent_to_contact boolean NOT NULL,
    interrogation_date date,
    possibly_implicated boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    version bigint NOT NULL,
    case_id uuid NOT NULL,
    id uuid NOT NULL,
    phone_number character varying(20),
    created_by_id character varying(100),
    first_name character varying(100),
    last_name character varying(100),
    profession character varying(100),
    updated_by_id character varying(100),
    email character varying(150),
    relation_with_parties character varying(200),
    address character varying(300),
    testimony_nature text
);


--
-- Name: agent agent_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent
    ADD CONSTRAINT agent_email_key UNIQUE (email);


--
-- Name: agent agent_keycloak_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent
    ADD CONSTRAINT agent_keycloak_id_key UNIQUE (keycloak_id);


--
-- Name: agent agent_matricule_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent
    ADD CONSTRAINT agent_matricule_key UNIQUE (matricule);


--
-- Name: agent agent_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent
    ADD CONSTRAINT agent_pkey PRIMARY KEY (id);


--
-- Name: attachment attachment_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT attachment_code_key UNIQUE (code);


--
-- Name: attachment attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT attachment_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: audition audition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audition
    ADD CONSTRAINT audition_pkey PRIMARY KEY (id);


--
-- Name: checklist_dossier_travail_coche checklist_dossier_travail_coche_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_dossier_travail_coche
    ADD CONSTRAINT checklist_dossier_travail_coche_pkey PRIMARY KEY (id);


--
-- Name: constitution_partie_civile constitution_partie_civile_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.constitution_partie_civile
    ADD CONSTRAINT constitution_partie_civile_investigation_id_key UNIQUE (investigation_id);


--
-- Name: constitution_partie_civile constitution_partie_civile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.constitution_partie_civile
    ADD CONSTRAINT constitution_partie_civile_pkey PRIMARY KEY (id);


--
-- Name: correction_pv_audition correction_pv_audition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.correction_pv_audition
    ADD CONSTRAINT correction_pv_audition_pkey PRIMARY KEY (id);


--
-- Name: decision_cge decision_cge_dossier_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision_cge
    ADD CONSTRAINT decision_cge_dossier_id_key UNIQUE (dossier_id);


--
-- Name: decision_cge decision_cge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision_cge
    ADD CONSTRAINT decision_cge_pkey PRIMARY KEY (id);


--
-- Name: declarant declarant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.declarant
    ADD CONSTRAINT declarant_pkey PRIMARY KEY (id);


--
-- Name: demande_documents demande_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_documents
    ADD CONSTRAINT demande_documents_pkey PRIMARY KEY (id);


--
-- Name: departement departement_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departement
    ADD CONSTRAINT departement_code_key UNIQUE (code);


--
-- Name: departement departement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departement
    ADD CONSTRAINT departement_pkey PRIMARY KEY (id);


--
-- Name: dossier dossier_access_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier
    ADD CONSTRAINT dossier_access_code_key UNIQUE (access_code);


--
-- Name: dossier_habilitation dossier_habilitation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_habilitation
    ADD CONSTRAINT dossier_habilitation_pkey PRIMARY KEY (id);


--
-- Name: dossier dossier_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier
    ADD CONSTRAINT dossier_number_key UNIQUE (number);


--
-- Name: dossier dossier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier
    ADD CONSTRAINT dossier_pkey PRIMARY KEY (id);


--
-- Name: engagement_confidentialite engagement_confidentialite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.engagement_confidentialite
    ADD CONSTRAINT engagement_confidentialite_pkey PRIMARY KEY (id);


--
-- Name: etude_opportunite etude_opportunite_dossier_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etude_opportunite
    ADD CONSTRAINT etude_opportunite_dossier_id_key UNIQUE (dossier_id);


--
-- Name: etude_opportunite etude_opportunite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etude_opportunite
    ADD CONSTRAINT etude_opportunite_pkey PRIMARY KEY (id);


--
-- Name: fiche_retex fiche_retex_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiche_retex
    ADD CONSTRAINT fiche_retex_investigation_id_key UNIQUE (investigation_id);


--
-- Name: fiche_retex fiche_retex_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiche_retex
    ADD CONSTRAINT fiche_retex_pkey PRIMARY KEY (id);


--
-- Name: checklist_dossier_travail_coche idx_checklist_coche_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_dossier_travail_coche
    ADD CONSTRAINT idx_checklist_coche_unique UNIQUE (investigation_id, point_id);


--
-- Name: information_preoccupante_dossier idx_ip_dossier_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.information_preoccupante_dossier
    ADD CONSTRAINT idx_ip_dossier_unique UNIQUE (information_preoccupante_id, dossier_id);


--
-- Name: seance_ctadp_dossier idx_seance_ctadp_dossier_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance_ctadp_dossier
    ADD CONSTRAINT idx_seance_ctadp_dossier_unique UNIQUE (seance_ctadp_id, dossier_id);


--
-- Name: incident_objectivite incident_objectivite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incident_objectivite
    ADD CONSTRAINT incident_objectivite_pkey PRIMARY KEY (id);


--
-- Name: indice_fraude indice_fraude_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indice_fraude
    ADD CONSTRAINT indice_fraude_code_key UNIQUE (code);


--
-- Name: indice_fraude indice_fraude_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indice_fraude
    ADD CONSTRAINT indice_fraude_pkey PRIMARY KEY (id);


--
-- Name: information_preoccupante_dossier information_preoccupante_dossier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.information_preoccupante_dossier
    ADD CONSTRAINT information_preoccupante_dossier_pkey PRIMARY KEY (id);


--
-- Name: information_preoccupante information_preoccupante_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.information_preoccupante
    ADD CONSTRAINT information_preoccupante_pkey PRIMARY KEY (id);


--
-- Name: investigation investigation_case_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT investigation_case_id_key UNIQUE (case_id);


--
-- Name: investigation_member investigation_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation_member
    ADD CONSTRAINT investigation_member_pkey PRIMARY KEY (id);


--
-- Name: investigation investigation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT investigation_pkey PRIMARY KEY (id);


--
-- Name: lecon_a_partager lecon_a_partager_fiche_retex_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lecon_a_partager
    ADD CONSTRAINT lecon_a_partager_fiche_retex_id_key UNIQUE (fiche_retex_id);


--
-- Name: lecon_a_partager lecon_a_partager_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lecon_a_partager
    ADD CONSTRAINT lecon_a_partager_pkey PRIMARY KEY (id);


--
-- Name: login_log login_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_log
    ADD CONSTRAINT login_log_pkey PRIMARY KEY (id);


--
-- Name: mandat mandat_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mandat
    ADD CONSTRAINT mandat_investigation_id_key UNIQUE (investigation_id);


--
-- Name: mandat mandat_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mandat
    ADD CONSTRAINT mandat_pkey PRIMARY KEY (id);


--
-- Name: mesure_conservatoire mesure_conservatoire_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mesure_conservatoire
    ADD CONSTRAINT mesure_conservatoire_pkey PRIMARY KEY (id);


--
-- Name: mission_suivi mission_suivi_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mission_suivi
    ADD CONSTRAINT mission_suivi_pkey PRIMARY KEY (id);


--
-- Name: note_avancement note_avancement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note_avancement
    ADD CONSTRAINT note_avancement_pkey PRIMARY KEY (id);


--
-- Name: note_recommandations note_recommandations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note_recommandations
    ADD CONSTRAINT note_recommandations_pkey PRIMARY KEY (id);


--
-- Name: note_recommandations note_recommandations_rapport_enquete_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note_recommandations
    ADD CONSTRAINT note_recommandations_rapport_enquete_id_key UNIQUE (rapport_enquete_id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: observation observation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.observation
    ADD CONSTRAINT observation_pkey PRIMARY KEY (id);


--
-- Name: parametre_delai parametre_delai_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametre_delai
    ADD CONSTRAINT parametre_delai_code_key UNIQUE (code);


--
-- Name: parametre_delai parametre_delai_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametre_delai
    ADD CONSTRAINT parametre_delai_pkey PRIMARY KEY (id);


--
-- Name: permission permission_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_permission_key_key UNIQUE (permission_key);


--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);


--
-- Name: plan_actions plan_actions_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_actions
    ADD CONSTRAINT plan_actions_investigation_id_key UNIQUE (investigation_id);


--
-- Name: plan_actions plan_actions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_actions
    ADD CONSTRAINT plan_actions_pkey PRIMARY KEY (id);


--
-- Name: plan_investigation plan_investigation_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_investigation
    ADD CONSTRAINT plan_investigation_investigation_id_key UNIQUE (investigation_id);


--
-- Name: plan_investigation plan_investigation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_investigation
    ADD CONSTRAINT plan_investigation_pkey PRIMARY KEY (id);


--
-- Name: point_checklist_dossier_travail point_checklist_dossier_travail_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_checklist_dossier_travail
    ADD CONSTRAINT point_checklist_dossier_travail_code_key UNIQUE (code);


--
-- Name: point_checklist_dossier_travail point_checklist_dossier_travail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.point_checklist_dossier_travail
    ADD CONSTRAINT point_checklist_dossier_travail_pkey PRIMARY KEY (id);


--
-- Name: portal_config portal_config_config_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.portal_config
    ADD CONSTRAINT portal_config_config_key_key UNIQUE (config_key);


--
-- Name: portal_config portal_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.portal_config
    ADD CONSTRAINT portal_config_pkey PRIMARY KEY (id);


--
-- Name: procedure_urgence procedure_urgence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procedure_urgence
    ADD CONSTRAINT procedure_urgence_pkey PRIMARY KEY (id);


--
-- Name: pv_audition pv_audition_audition_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_audition
    ADD CONSTRAINT pv_audition_audition_id_key UNIQUE (audition_id);


--
-- Name: pv_audition pv_audition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_audition
    ADD CONSTRAINT pv_audition_pkey PRIMARY KEY (id);


--
-- Name: pv_constat pv_constat_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_constat
    ADD CONSTRAINT pv_constat_pkey PRIMARY KEY (id);


--
-- Name: pv_constat pv_constat_visite_terrain_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_constat
    ADD CONSTRAINT pv_constat_visite_terrain_id_key UNIQUE (visite_terrain_id);


--
-- Name: rapport_enquete rapport_enquete_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rapport_enquete
    ADD CONSTRAINT rapport_enquete_investigation_id_key UNIQUE (investigation_id);


--
-- Name: rapport_enquete rapport_enquete_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rapport_enquete
    ADD CONSTRAINT rapport_enquete_pkey PRIMARY KEY (id);


--
-- Name: relance_suites relance_suites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.relance_suites
    ADD CONSTRAINT relance_suites_pkey PRIMARY KEY (id);


--
-- Name: requete_parquet requete_parquet_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requete_parquet
    ADD CONSTRAINT requete_parquet_investigation_id_key UNIQUE (investigation_id);


--
-- Name: requete_parquet requete_parquet_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requete_parquet
    ADD CONSTRAINT requete_parquet_pkey PRIMARY KEY (id);


--
-- Name: revision_plan revision_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revision_plan
    ADD CONSTRAINT revision_plan_pkey PRIMARY KEY (id);


--
-- Name: role_definition role_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_definition
    ADD CONSTRAINT role_definition_pkey PRIMARY KEY (id);


--
-- Name: role_definition role_definition_role_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_definition
    ADD CONSTRAINT role_definition_role_key_key UNIQUE (role_key);


--
-- Name: role_permission role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT role_permission_pkey PRIMARY KEY (permission_id, role_id);


--
-- Name: seance_ctadp_dossier seance_ctadp_dossier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance_ctadp_dossier
    ADD CONSTRAINT seance_ctadp_dossier_pkey PRIMARY KEY (id);


--
-- Name: seance_ctadp seance_ctadp_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance_ctadp
    ADD CONSTRAINT seance_ctadp_pkey PRIMARY KEY (id);


--
-- Name: section_dossier_travail section_dossier_travail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.section_dossier_travail
    ADD CONSTRAINT section_dossier_travail_pkey PRIMARY KEY (id);


--
-- Name: status_history status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.status_history
    ADD CONSTRAINT status_history_pkey PRIMARY KEY (id);


--
-- Name: suivi_procedure_penale suivi_procedure_penale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suivi_procedure_penale
    ADD CONSTRAINT suivi_procedure_penale_pkey PRIMARY KEY (id);


--
-- Name: targeted_party targeted_party_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targeted_party
    ADD CONSTRAINT targeted_party_pkey PRIMARY KEY (id);


--
-- Name: transmission_autorite transmission_autorite_investigation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transmission_autorite
    ADD CONSTRAINT transmission_autorite_investigation_id_key UNIQUE (investigation_id);


--
-- Name: transmission_autorite transmission_autorite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transmission_autorite
    ADD CONSTRAINT transmission_autorite_pkey PRIMARY KEY (id);


--
-- Name: type_declarant_config type_declarant_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_declarant_config
    ADD CONSTRAINT type_declarant_config_pkey PRIMARY KEY (id);


--
-- Name: type_declarant_config type_declarant_config_type_declarant_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_declarant_config
    ADD CONSTRAINT type_declarant_config_type_declarant_key UNIQUE (type_declarant);


--
-- Name: type_infraction type_infraction_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_infraction
    ADD CONSTRAINT type_infraction_code_key UNIQUE (code);


--
-- Name: type_infraction type_infraction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_infraction
    ADD CONSTRAINT type_infraction_pkey PRIMARY KEY (id);


--
-- Name: visite_terrain visite_terrain_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visite_terrain
    ADD CONSTRAINT visite_terrain_pkey PRIMARY KEY (id);


--
-- Name: witness witness_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.witness
    ADD CONSTRAINT witness_pkey PRIMARY KEY (id);


--
-- Name: idx_attachment_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_case ON public.attachment USING btree (case_id);


--
-- Name: idx_attachment_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_investigation ON public.attachment USING btree (investigation_id);


--
-- Name: idx_attachment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_status ON public.attachment USING btree (status);


--
-- Name: idx_audition_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audition_investigation ON public.audition USING btree (investigation_id);


--
-- Name: idx_checklist_coche_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_checklist_coche_investigation ON public.checklist_dossier_travail_coche USING btree (investigation_id);


--
-- Name: idx_correction_pv_audition_pv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_correction_pv_audition_pv ON public.correction_pv_audition USING btree (pv_audition_id);


--
-- Name: idx_declarant_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_declarant_email ON public.declarant USING btree (email);


--
-- Name: idx_declarant_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_declarant_phone ON public.declarant USING btree (phone_number);


--
-- Name: idx_declarant_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_declarant_type ON public.declarant USING btree (type_declarant);


--
-- Name: idx_demande_documents_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_demande_documents_investigation ON public.demande_documents USING btree (investigation_id);


--
-- Name: idx_dossier_agent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dossier_agent ON public.dossier USING btree (agent_in_charge_id);


--
-- Name: idx_dossier_declarant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dossier_declarant ON public.dossier USING btree (declarant_id);


--
-- Name: idx_dossier_reception; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dossier_reception ON public.dossier USING btree (reception_date);


--
-- Name: idx_dossier_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dossier_status ON public.dossier USING btree (status);


--
-- Name: idx_habilitation_agent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_habilitation_agent ON public.dossier_habilitation USING btree (agent_id);


--
-- Name: idx_habilitation_dossier_agent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_habilitation_dossier_agent ON public.dossier_habilitation USING btree (dossier_id, agent_id);


--
-- Name: idx_incident_objectivite_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incident_objectivite_investigation ON public.incident_objectivite USING btree (investigation_id);


--
-- Name: idx_inv_member_agent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_member_agent ON public.investigation_member USING btree (agent_id);


--
-- Name: idx_inv_member_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_member_investigation ON public.investigation_member USING btree (investigation_id);


--
-- Name: idx_investigation_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_investigation_status ON public.investigation USING btree (status);


--
-- Name: idx_ip_dossier_dossier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ip_dossier_dossier ON public.information_preoccupante_dossier USING btree (dossier_id);


--
-- Name: idx_ip_dossier_information; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ip_dossier_information ON public.information_preoccupante_dossier USING btree (information_preoccupante_id);


--
-- Name: idx_mesure_conservatoire_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mesure_conservatoire_investigation ON public.mesure_conservatoire USING btree (investigation_id);


--
-- Name: idx_mission_suivi_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mission_suivi_investigation ON public.mission_suivi USING btree (investigation_id);


--
-- Name: idx_notification_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_case ON public.notification USING btree (case_id);


--
-- Name: idx_notification_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_scheduled ON public.notification USING btree (scheduled_at);


--
-- Name: idx_notification_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_status ON public.notification USING btree (status);


--
-- Name: idx_observation_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_observation_case ON public.observation USING btree (case_id);


--
-- Name: idx_observation_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_observation_created_at ON public.observation USING btree (created_at);


--
-- Name: idx_procedure_urgence_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_procedure_urgence_investigation ON public.procedure_urgence USING btree (investigation_id);


--
-- Name: idx_revision_plan_plan_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_revision_plan_plan_investigation ON public.revision_plan USING btree (plan_investigation_id);


--
-- Name: idx_seance_ctadp_dossier_dossier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seance_ctadp_dossier_dossier ON public.seance_ctadp_dossier USING btree (dossier_id);


--
-- Name: idx_seance_ctadp_dossier_seance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seance_ctadp_dossier_seance ON public.seance_ctadp_dossier USING btree (seance_ctadp_id);


--
-- Name: idx_section_dossier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_section_dossier ON public.section_dossier_travail USING btree (dossier_id);


--
-- Name: idx_section_dossier_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_section_dossier_type ON public.section_dossier_travail USING btree (dossier_id, type);


--
-- Name: idx_status_history_agent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_status_history_agent ON public.status_history USING btree (agent_id);


--
-- Name: idx_status_history_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_status_history_case ON public.status_history USING btree (case_id);


--
-- Name: idx_status_history_changed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_status_history_changed_at ON public.status_history USING btree (changed_at);


--
-- Name: idx_suivi_procedure_penale_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_suivi_procedure_penale_investigation ON public.suivi_procedure_penale USING btree (investigation_id);


--
-- Name: idx_targeted_party_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_targeted_party_case ON public.targeted_party USING btree (case_id);


--
-- Name: idx_visite_terrain_investigation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_visite_terrain_investigation ON public.visite_terrain USING btree (investigation_id);


--
-- Name: idx_witness_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_witness_case ON public.witness USING btree (case_id);


--
-- Name: investigation_member fk1da0wxytr75gft6meny7wh68r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation_member
    ADD CONSTRAINT fk1da0wxytr75gft6meny7wh68r FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: status_history fk1jhdv360o1fcr6wkjk5lc8hhm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.status_history
    ADD CONSTRAINT fk1jhdv360o1fcr6wkjk5lc8hhm FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: note_recommandations fk1no67dj47yjew992cnnxsbio1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note_recommandations
    ADD CONSTRAINT fk1no67dj47yjew992cnnxsbio1 FOREIGN KEY (rapport_enquete_id) REFERENCES public.rapport_enquete(id);


--
-- Name: procedure_urgence fk1o3ewpxoeomc6g48rp3beqjma; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procedure_urgence
    ADD CONSTRAINT fk1o3ewpxoeomc6g48rp3beqjma FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: attachment fk1tt6n6pvhs908ibnh0m23s06e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT fk1tt6n6pvhs908ibnh0m23s06e FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: incident_objectivite fk1yk4s4ccumfm5oksp13k2tohr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incident_objectivite
    ADD CONSTRAINT fk1yk4s4ccumfm5oksp13k2tohr FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: audition fk21qgs0pagso71og72oqjbp88v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audition
    ADD CONSTRAINT fk21qgs0pagso71og72oqjbp88v FOREIGN KEY (witness_id) REFERENCES public.witness(id);


--
-- Name: visite_terrain fk2lo39dbrsc1mtyq2du6sfr35s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visite_terrain
    ADD CONSTRAINT fk2lo39dbrsc1mtyq2du6sfr35s FOREIGN KEY (conducted_by_id) REFERENCES public.agent(id);


--
-- Name: mandat fk2oge9vyvhcodwsv1rseoy43hi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mandat
    ADD CONSTRAINT fk2oge9vyvhcodwsv1rseoy43hi FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: suivi_procedure_penale fk2tft79mhgso24q28d4dt56y5f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suivi_procedure_penale
    ADD CONSTRAINT fk2tft79mhgso24q28d4dt56y5f FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: dossier_habilitation fk37brb8ap5janr7sqr8pu6bilb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_habilitation
    ADD CONSTRAINT fk37brb8ap5janr7sqr8pu6bilb FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: transmission_autorite fk3i3r6aydp470jhpwh86cbirw0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transmission_autorite
    ADD CONSTRAINT fk3i3r6aydp470jhpwh86cbirw0 FOREIGN KEY (transmitted_by_id) REFERENCES public.agent(id);


--
-- Name: mesure_conservatoire fk4uuvd3hm4jq8c9l8frathl8n3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mesure_conservatoire
    ADD CONSTRAINT fk4uuvd3hm4jq8c9l8frathl8n3 FOREIGN KEY (taken_by_id) REFERENCES public.agent(id);


--
-- Name: engagement_confidentialite fk51kupegeeyw6ek0me2vvkjpvm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.engagement_confidentialite
    ADD CONSTRAINT fk51kupegeeyw6ek0me2vvkjpvm FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: observation fk56twoxl74b8b020on0i9uw9g5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.observation
    ADD CONSTRAINT fk56twoxl74b8b020on0i9uw9g5 FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: checklist_dossier_travail_coche fk5iv5l1e9j15m2jekrk56h05hx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_dossier_travail_coche
    ADD CONSTRAINT fk5iv5l1e9j15m2jekrk56h05hx FOREIGN KEY (point_id) REFERENCES public.point_checklist_dossier_travail(id);


--
-- Name: notification fk5rvcksbse3ymk8v1qvg7bti1b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk5rvcksbse3ymk8v1qvg7bti1b FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: relance_suites fk5ssxkby6arkfv30sk54v1rjfj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.relance_suites
    ADD CONSTRAINT fk5ssxkby6arkfv30sk54v1rjfj FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: notification fk64yge1pl92o6yuefm3y5qn5dl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk64yge1pl92o6yuefm3y5qn5dl FOREIGN KEY (signed_by_id) REFERENCES public.agent(id);


--
-- Name: status_history fk6dawhgdua5n7k0ocg9xe98e1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.status_history
    ADD CONSTRAINT fk6dawhgdua5n7k0ocg9xe98e1 FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: plan_investigation fk7hhmsqtcex326yhjbsssraobj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_investigation
    ADD CONSTRAINT fk7hhmsqtcex326yhjbsssraobj FOREIGN KEY (validated_by_id) REFERENCES public.agent(id);


--
-- Name: constitution_partie_civile fk7ope5u54oa1od93lt27hwvnhl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.constitution_partie_civile
    ADD CONSTRAINT fk7ope5u54oa1od93lt27hwvnhl FOREIGN KEY (constituee_par_id) REFERENCES public.agent(id);


--
-- Name: audition fk7skqrpioq2b0a0l457enxik8f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audition
    ADD CONSTRAINT fk7skqrpioq2b0a0l457enxik8f FOREIGN KEY (targeted_party_id) REFERENCES public.targeted_party(id);


--
-- Name: correction_pv_audition fk7vkf4cvx2kfdm1vr1btmnmvqo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.correction_pv_audition
    ADD CONSTRAINT fk7vkf4cvx2kfdm1vr1btmnmvqo FOREIGN KEY (pv_audition_id) REFERENCES public.pv_audition(id);


--
-- Name: demande_documents fk8tmnhvxvldfbhxvrek47qu86t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_documents
    ADD CONSTRAINT fk8tmnhvxvldfbhxvrek47qu86t FOREIGN KEY (requested_by_id) REFERENCES public.agent(id);


--
-- Name: mandat fk8y3tpho9akg7qvrtvjp6vdjo9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mandat
    ADD CONSTRAINT fk8y3tpho9akg7qvrtvjp6vdjo9 FOREIGN KEY (agent_cge_id) REFERENCES public.agent(id);


--
-- Name: information_preoccupante_dossier fk95jp3mog7c2dsncpiox691qfs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.information_preoccupante_dossier
    ADD CONSTRAINT fk95jp3mog7c2dsncpiox691qfs FOREIGN KEY (dossier_id) REFERENCES public.dossier(id);


--
-- Name: investigation_member fk99irt214xh779j8vsiuqhtx25; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation_member
    ADD CONSTRAINT fk99irt214xh779j8vsiuqhtx25 FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: correction_pv_audition fka4f8tcm7o0src5n03mck7p2ko; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.correction_pv_audition
    ADD CONSTRAINT fka4f8tcm7o0src5n03mck7p2ko FOREIGN KEY (corrected_by_id) REFERENCES public.agent(id);


--
-- Name: constitution_partie_civile fka8fyj8l4fa1gdre2rky7sbdbb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.constitution_partie_civile
    ADD CONSTRAINT fka8fyj8l4fa1gdre2rky7sbdbb FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: plan_actions fkacfeeltdn7j916y0u1u6j2mvv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_actions
    ADD CONSTRAINT fkacfeeltdn7j916y0u1u6j2mvv FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: pv_constat fkackp2apxonq0hpm0mu0vg4rix; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_constat
    ADD CONSTRAINT fkackp2apxonq0hpm0mu0vg4rix FOREIGN KEY (visite_terrain_id) REFERENCES public.visite_terrain(id);


--
-- Name: requete_parquet fkap3ko94ogxdplep16ri1jlf6m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requete_parquet
    ADD CONSTRAINT fkap3ko94ogxdplep16ri1jlf6m FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: audition fkb54lci8nqnap94n9uosl1vm6w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audition
    ADD CONSTRAINT fkb54lci8nqnap94n9uosl1vm6w FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: dossier_habilitation fkbihmjiwb02u6iyf6kdri1fd7b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_habilitation
    ADD CONSTRAINT fkbihmjiwb02u6iyf6kdri1fd7b FOREIGN KEY (granted_by_id) REFERENCES public.agent(id);


--
-- Name: investigation fkc0tj8ete0vpo35motv1ggnj6q; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fkc0tj8ete0vpo35motv1ggnj6q FOREIGN KEY (cge_approved_by_id) REFERENCES public.agent(id);


--
-- Name: investigation fkc4jwwm591tki79kdbojaf2de9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fkc4jwwm591tki79kdbojaf2de9 FOREIGN KEY (extension_approved_by_id) REFERENCES public.agent(id);


--
-- Name: decision_cge fkc788t6msopyobycnui0vjdwl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision_cge
    ADD CONSTRAINT fkc788t6msopyobycnui0vjdwl FOREIGN KEY (agent_cge_id) REFERENCES public.agent(id);


--
-- Name: mission_suivi fkc94qu7qcogymoho2upa9wd1r5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mission_suivi
    ADD CONSTRAINT fkc94qu7qcogymoho2upa9wd1r5 FOREIGN KEY (conducted_by_id) REFERENCES public.agent(id);


--
-- Name: dossier_habilitation fkcro2bo7ldg8kopkqs3qhy9ieu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_habilitation
    ADD CONSTRAINT fkcro2bo7ldg8kopkqs3qhy9ieu FOREIGN KEY (revoked_by_id) REFERENCES public.agent(id);


--
-- Name: observation fkdcxws7ghm96m6h7eju4accnki; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.observation
    ADD CONSTRAINT fkdcxws7ghm96m6h7eju4accnki FOREIGN KEY (author_id) REFERENCES public.agent(id);


--
-- Name: mission_suivi fkdhkk9qnwm3qxu2vgdfjfm2ivh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mission_suivi
    ADD CONSTRAINT fkdhkk9qnwm3qxu2vgdfjfm2ivh FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: procedure_urgence fkds8xwspfco8e22ewbx5eutd6j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procedure_urgence
    ADD CONSTRAINT fkds8xwspfco8e22ewbx5eutd6j FOREIGN KEY (decided_by_id) REFERENCES public.agent(id);


--
-- Name: role_permission fke6onrsc6iwvq3c12jl300ge6b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fke6onrsc6iwvq3c12jl300ge6b FOREIGN KEY (role_id) REFERENCES public.role_definition(id);


--
-- Name: attachment fkf0j71tbwaji9dgspkl3e29gws; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT fkf0j71tbwaji9dgspkl3e29gws FOREIGN KEY (uploaded_by_id) REFERENCES public.agent(id);


--
-- Name: agent fkf6f8c48lqpommr8l97cic460t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent
    ADD CONSTRAINT fkf6f8c48lqpommr8l97cic460t FOREIGN KEY (departement_id) REFERENCES public.departement(id);


--
-- Name: role_permission fkf8yllw1ecvwqy3ehyxawqa1qp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fkf8yllw1ecvwqy3ehyxawqa1qp FOREIGN KEY (permission_id) REFERENCES public.permission(id);


--
-- Name: attachment fkfid11q8plh8rbqs37av6cmkd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT fkfid11q8plh8rbqs37av6cmkd FOREIGN KEY (validated_by_id) REFERENCES public.agent(id);


--
-- Name: audition_investigator fkfvbrqh343xs4sjqi0glnnxkwu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audition_investigator
    ADD CONSTRAINT fkfvbrqh343xs4sjqi0glnnxkwu FOREIGN KEY (audition_id) REFERENCES public.audition(id);


--
-- Name: decision_cge fkfwyed66irmkhn8v3rp4vsbfo4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision_cge
    ADD CONSTRAINT fkfwyed66irmkhn8v3rp4vsbfo4 FOREIGN KEY (dossier_id) REFERENCES public.dossier(id);


--
-- Name: investigation fkgcllls24yye7hemigyn03cinc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fkgcllls24yye7hemigyn03cinc FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: targeted_party fkgjiij18xxk1h5hrtq3m3ea60o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.targeted_party
    ADD CONSTRAINT fkgjiij18xxk1h5hrtq3m3ea60o FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: dossier fkgwnq91ny8n4yb9wc43qj0vj62; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier
    ADD CONSTRAINT fkgwnq91ny8n4yb9wc43qj0vj62 FOREIGN KEY (priority_set_by_id) REFERENCES public.agent(id);


--
-- Name: fiche_retex fkh01m6nxmawbc241x71bn3jm1s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiche_retex
    ADD CONSTRAINT fkh01m6nxmawbc241x71bn3jm1s FOREIGN KEY (redige_par_id) REFERENCES public.agent(id);


--
-- Name: pv_audition fkhf0yowm8umcl8ocg61bk6jqpr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_audition
    ADD CONSTRAINT fkhf0yowm8umcl8ocg61bk6jqpr FOREIGN KEY (audition_id) REFERENCES public.audition(id);


--
-- Name: etude_opportunite fkhqa90d158ktp0sh0hjr3uysdf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etude_opportunite
    ADD CONSTRAINT fkhqa90d158ktp0sh0hjr3uysdf FOREIGN KEY (dossier_id) REFERENCES public.dossier(id);


--
-- Name: note_avancement fki60t1kh57umctgrcxy9375odh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note_avancement
    ADD CONSTRAINT fki60t1kh57umctgrcxy9375odh FOREIGN KEY (plan_actions_id) REFERENCES public.plan_actions(id);


--
-- Name: note_avancement fkia6cx32olctfq96bknxiy8s0r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.note_avancement
    ADD CONSTRAINT fkia6cx32olctfq96bknxiy8s0r FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: attachment fkimu8khonihtkpltdgq8mrc6v1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT fkimu8khonihtkpltdgq8mrc6v1 FOREIGN KEY (section_id) REFERENCES public.section_dossier_travail(id);


--
-- Name: visite_terrain fkinkos3hwovopmoga2ex40sq3o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visite_terrain
    ADD CONSTRAINT fkinkos3hwovopmoga2ex40sq3o FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: plan_investigation fkiqp4d75fems2avb6w3i5glfou; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_investigation
    ADD CONSTRAINT fkiqp4d75fems2avb6w3i5glfou FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: investigation fkj2hkfacplhi6x01ivobyb2xpc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fkj2hkfacplhi6x01ivobyb2xpc FOREIGN KEY (cgea_id) REFERENCES public.agent(id);


--
-- Name: dossier fkkaeu8m9itbparu6nr5dh12675; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier
    ADD CONSTRAINT fkkaeu8m9itbparu6nr5dh12675 FOREIGN KEY (declarant_id) REFERENCES public.declarant(id);


--
-- Name: checklist_dossier_travail_coche fkkcixg6asottlisg9hjribbpdu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_dossier_travail_coche
    ADD CONSTRAINT fkkcixg6asottlisg9hjribbpdu FOREIGN KEY (coche_par_id) REFERENCES public.agent(id);


--
-- Name: fiche_retex fkkev67wq73iyiy04o3pjnphobk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiche_retex
    ADD CONSTRAINT fkkev67wq73iyiy04o3pjnphobk FOREIGN KEY (type_infraction_id) REFERENCES public.type_infraction(id);


--
-- Name: lecon_a_partager fkleboam58igwfxtqwvu6okqw7i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lecon_a_partager
    ADD CONSTRAINT fkleboam58igwfxtqwvu6okqw7i FOREIGN KEY (publiee_par_id) REFERENCES public.agent(id);


--
-- Name: investigation fklwc6pawlrac539ojqs6tdv9mr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fklwc6pawlrac539ojqs6tdv9mr FOREIGN KEY (legal_advisor_approved_by_id) REFERENCES public.agent(id);


--
-- Name: relance_suites fkm1yp9jjcm0h4pparllk4r2vn8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.relance_suites
    ADD CONSTRAINT fkm1yp9jjcm0h4pparllk4r2vn8 FOREIGN KEY (transmission_autorite_id) REFERENCES public.transmission_autorite(id);


--
-- Name: audition_investigator fkm3yfu0ktyi86dx5mh476j959t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audition_investigator
    ADD CONSTRAINT fkm3yfu0ktyi86dx5mh476j959t FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: dossier_habilitation fkmev8yfwsvtx4xa99ryt2umf9s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier_habilitation
    ADD CONSTRAINT fkmev8yfwsvtx4xa99ryt2umf9s FOREIGN KEY (dossier_id) REFERENCES public.dossier(id);


--
-- Name: seance_ctadp_dossier fkmh44p51nqqlgqi0whfgbdg4ym; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance_ctadp_dossier
    ADD CONSTRAINT fkmh44p51nqqlgqi0whfgbdg4ym FOREIGN KEY (dossier_id) REFERENCES public.dossier(id);


--
-- Name: suivi_procedure_penale fkmpn353xgcbtx992gn8t02f9s4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suivi_procedure_penale
    ADD CONSTRAINT fkmpn353xgcbtx992gn8t02f9s4 FOREIGN KEY (agent_id) REFERENCES public.agent(id);


--
-- Name: lecon_a_partager fkmteg68p8mjokdrdkhpncqbics; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lecon_a_partager
    ADD CONSTRAINT fkmteg68p8mjokdrdkhpncqbics FOREIGN KEY (fiche_retex_id) REFERENCES public.fiche_retex(id);


--
-- Name: revision_plan fkmu8sw5csrl65u832phw4frvlb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revision_plan
    ADD CONSTRAINT fkmu8sw5csrl65u832phw4frvlb FOREIGN KEY (plan_investigation_id) REFERENCES public.plan_investigation(id);


--
-- Name: pv_audition fkmvvenou3yf0fi3l2qto7u0l1v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_audition
    ADD CONSTRAINT fkmvvenou3yf0fi3l2qto7u0l1v FOREIGN KEY (drafted_by_id) REFERENCES public.agent(id);


--
-- Name: attachment fkn32vdcojg7irckl6bwkxorodd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT fkn32vdcojg7irckl6bwkxorodd FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: revision_plan fknh9op570f3ks07l5ilt4b624s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revision_plan
    ADD CONSTRAINT fknh9op570f3ks07l5ilt4b624s FOREIGN KEY (revised_by_id) REFERENCES public.agent(id);


--
-- Name: mesure_conservatoire fko1p5twqoog556hxhq8e3pm43f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mesure_conservatoire
    ADD CONSTRAINT fko1p5twqoog556hxhq8e3pm43f FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: dossier fkof4g18321vqt2j3eb18gmdhsl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dossier
    ADD CONSTRAINT fkof4g18321vqt2j3eb18gmdhsl FOREIGN KEY (agent_in_charge_id) REFERENCES public.agent(id);


--
-- Name: information_preoccupante_dossier fkogb3exlkkisdj3mwh75dcflfv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.information_preoccupante_dossier
    ADD CONSTRAINT fkogb3exlkkisdj3mwh75dcflfv FOREIGN KEY (information_preoccupante_id) REFERENCES public.information_preoccupante(id);


--
-- Name: seance_ctadp_dossier fkp35f63cahcs7abx1ktlqdhu2c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance_ctadp_dossier
    ADD CONSTRAINT fkp35f63cahcs7abx1ktlqdhu2c FOREIGN KEY (seance_ctadp_id) REFERENCES public.seance_ctadp(id);


--
-- Name: etude_opportunite fkpfekbh01yevdo6s65ib87q09m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etude_opportunite
    ADD CONSTRAINT fkpfekbh01yevdo6s65ib87q09m FOREIGN KEY (type_infraction_id) REFERENCES public.type_infraction(id);


--
-- Name: transmission_autorite fkpop8o8ng9eivfe9xkylualom2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transmission_autorite
    ADD CONSTRAINT fkpop8o8ng9eivfe9xkylualom2 FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: demande_documents fkpxsur50516s1n1vh0nsj84hev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_documents
    ADD CONSTRAINT fkpxsur50516s1n1vh0nsj84hev FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: fiche_retex fkqjc4d6h0d5sehc7ar2c4sfsy3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiche_retex
    ADD CONSTRAINT fkqjc4d6h0d5sehc7ar2c4sfsy3 FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: procedure_urgence fkqohjcs936l08hm3niytp4ynhx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procedure_urgence
    ADD CONSTRAINT fkqohjcs936l08hm3niytp4ynhx FOREIGN KEY (requested_by_id) REFERENCES public.agent(id);


--
-- Name: plan_investigation fkqtfp6vhoetax53cvsqxkbtwt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_investigation
    ADD CONSTRAINT fkqtfp6vhoetax53cvsqxkbtwt FOREIGN KEY (submitted_by_id) REFERENCES public.agent(id);


--
-- Name: engagement_confidentialite fkrhxxmapplf3h5hapt98au0uai; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.engagement_confidentialite
    ADD CONSTRAINT fkrhxxmapplf3h5hapt98au0uai FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: pv_constat fks7q5103novpildw69ouayk4qn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pv_constat
    ADD CONSTRAINT fks7q5103novpildw69ouayk4qn FOREIGN KEY (drafted_by_id) REFERENCES public.agent(id);


--
-- Name: investigation fkscy49edlrkei4vlt5bug8fboi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fkscy49edlrkei4vlt5bug8fboi FOREIGN KEY (dei_approved_by_id) REFERENCES public.agent(id);


--
-- Name: incident_objectivite fksyc6s14hh3xm4om90cu79wyq3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incident_objectivite
    ADD CONSTRAINT fksyc6s14hh3xm4om90cu79wyq3 FOREIGN KEY (declared_by_id) REFERENCES public.agent(id);


--
-- Name: witness fkt9sipj9o36ag9pso8si3v3s0v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.witness
    ADD CONSTRAINT fkt9sipj9o36ag9pso8si3v3s0v FOREIGN KEY (case_id) REFERENCES public.dossier(id);


--
-- Name: checklist_dossier_travail_coche fktb5k84wao61dpl6kedxsetjva; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_dossier_travail_coche
    ADD CONSTRAINT fktb5k84wao61dpl6kedxsetjva FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: section_dossier_travail fktcri9ci8p8ug4kvcacm3kmh74; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.section_dossier_travail
    ADD CONSTRAINT fktcri9ci8p8ug4kvcacm3kmh74 FOREIGN KEY (dossier_id) REFERENCES public.dossier(id);


--
-- Name: rapport_enquete fktf1g9lxvn0fpxd0motryaqluf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rapport_enquete
    ADD CONSTRAINT fktf1g9lxvn0fpxd0motryaqluf FOREIGN KEY (investigation_id) REFERENCES public.investigation(id);


--
-- Name: investigation fktk5qvub3ekimho12v68bcn5cy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investigation
    ADD CONSTRAINT fktk5qvub3ekimho12v68bcn5cy FOREIGN KEY (cgea_approved_by_id) REFERENCES public.agent(id);


--
-- Name: plan_actions fku38i9uj2cv85eij2l6tus7x0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_actions
    ADD CONSTRAINT fku38i9uj2cv85eij2l6tus7x0 FOREIGN KEY (received_by_id) REFERENCES public.agent(id);


--
-- PostgreSQL database dump complete
--


