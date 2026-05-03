package gov.bf.ascelc.univers_audits.shared.utils;

/**
 * Constantes centralisées des URLs de l'API.
 * Chaque constante correspond à un endpoint réellement
 * exposé dans un contrôleur.
 */
public final class ApiUrls {

    private ApiUrls() {}

    private static final String API     = "/api";
    private static final String VERSION = "/v1";
    private static final String BASE    = API + VERSION;

    // ── Dossiers ──────────────────────────────────────────────

    public static final String DOSSIERS   = BASE + "/dossiers";

    // Portail public — sans authentification
    public static final String DOSSIERS_TRACK  = DOSSIERS + "/public/track/{accessCode}";
    public static final String DOSSIERS_SUBMIT = DOSSIERS + "/public/submit";

    // Filtres
    public static final String DOSSIERS_BY_STATUS = DOSSIERS + "/status/{status}";
    public static final String DOSSIERS_MY        = DOSSIERS + "/my";

    // Transitions de statut sur un dossier existant
    public static final String DOSSIERS_REGISTER     = DOSSIERS + "/{id}/register";
    public static final String DOSSIERS_START_STUDY  = DOSSIERS + "/{id}/start-study";
    public static final String DOSSIERS_REQ_COMPL    = DOSSIERS + "/{id}/request-complement";
    public static final String DOSSIERS_COMPL_RECV   = DOSSIERS + "/{id}/complement-received";
    public static final String DOSSIERS_CTADP        = DOSSIERS + "/{id}/submit-ctadp";
    public static final String DOSSIERS_ADMISSIBLE   = DOSSIERS + "/{id}/declare-admissible";
    public static final String DOSSIERS_INADMISSIBLE = DOSSIERS + "/{id}/declare-inadmissible";
    public static final String DOSSIERS_TRANSFER     = DOSSIERS + "/{id}/transfer";
    public static final String DOSSIERS_CLOSE        = DOSSIERS + "/{id}/close";

    // ── Parties visées (sous-ressource d'un dossier) ──────────

    public static final String PARTIES       = DOSSIERS + "/{dossierId}/parties";
    public static final String PARTIES_BY_ID = DOSSIERS + "/{dossierId}/parties/{partyId}";

    // ── Témoins (sous-ressource d'un dossier) ─────────────────

    public static final String WITNESSES       = DOSSIERS + "/{dossierId}/witnesses";
    public static final String WITNESSES_BY_ID = DOSSIERS + "/{dossierId}/witnesses/{witnessId}";

    // ── Observations (sous-ressource d'un dossier) ────────────

    public static final String OBSERVATIONS = DOSSIERS + "/{dossierId}/observations";

    // ── Investigations ────────────────────────────────────────

    public static final String INVESTIGATIONS            = BASE + "/investigations";
    public static final String INVESTIGATIONS_OVERDUE    = INVESTIGATIONS + "/overdue";
    public static final String INVESTIGATIONS_BY_DOSSIER = INVESTIGATIONS + "/dossier/{dossierId}";
    public static final String INVESTIGATIONS_OPEN       = INVESTIGATIONS + "/dossier/{dossierId}/open";

    // Cycle de vie
    public static final String INVESTIGATIONS_START   = INVESTIGATIONS + "/{id}/start";
    public static final String INVESTIGATIONS_SUSPEND = INVESTIGATIONS + "/{id}/suspend";
    public static final String INVESTIGATIONS_RESUME  = INVESTIGATIONS + "/{id}/resume";
    public static final String INVESTIGATIONS_EXTEND  = INVESTIGATIONS + "/{id}/extend-deadline";
    public static final String INVESTIGATIONS_REPORT  = INVESTIGATIONS + "/{id}/submit-report";

    // Circuit d'approbation
    public static final String INVESTIGATIONS_APPROVE_DEI = INVESTIGATIONS + "/{id}/approve-dei";
    public static final String INVESTIGATIONS_APPROVE_LEG = INVESTIGATIONS + "/{id}/approve-legal";
    public static final String INVESTIGATIONS_APPROVE_CGE = INVESTIGATIONS + "/{id}/approve-cge";

    // Gestion équipe
    public static final String INVESTIGATIONS_MEMBERS      = INVESTIGATIONS + "/{id}/members";
    public static final String INVESTIGATIONS_MEMBER_BY_ID = INVESTIGATIONS + "/{id}/members/{agentId}";

    // ── Notifications ─────────────────────────────────────────

    public static final String NOTIFICATIONS            = BASE + "/notifications";
    public static final String NOTIFICATIONS_BY_DOSSIER = NOTIFICATIONS + "/dossier/{dossierId}";
    public static final String NOTIFICATIONS_OVERDUE    = NOTIFICATIONS + "/overdue";
    public static final String NOTIFICATIONS_PENDING    = NOTIFICATIONS + "/pending";
    public static final String NOTIFICATIONS_SEND       = NOTIFICATIONS + "/{id}/send";
    public static final String NOTIFICATIONS_CANCEL     = NOTIFICATIONS + "/{id}/cancel";
    public static final String NOTIFICATIONS_RETRY      = NOTIFICATIONS + "/{id}/retry";
    public static final String NOTIFICATIONS_MY         = NOTIFICATIONS + "/my";

    // ── Statistiques ──────────────────────────────────────────

    public static final String STATS           = BASE + "/stats";
    public static final String STATS_PUBLIC    = STATS + "/public";
    public static final String STATS_DASHBOARD = STATS + "/dashboard";
    public static final String STATS_QUARTERLY = STATS + "/quarterly";
    public static final String STATS_ANNUAL    = STATS + "/annual";

    // ── Agents ────────────────────────────────────────────────

    public static final String AGENTS              = BASE + "/agents";
    public static final String AGENTS_ACTIVE       = AGENTS + "/active";
    public static final String AGENTS_KEYCLOAK_ROLES = AGENTS + "/keycloak-roles";

    // ── Configuration — référentiels ──────────────────────────

    public static final String CONFIG                 = BASE + "/config";
    public static final String CONFIG_TYPES_DECLARANT = CONFIG + "/types-declarant";
    public static final String CONFIG_DEPARTEMENTS    = CONFIG + "/departements";
    public static final String CONFIG_ENUMS           = CONFIG + "/enums";

    // ── Pièces jointes ────────────────────────────────────────

    public static final String ATTACHMENTS            = BASE + "/attachments";
    public static final String ATTACHMENTS_BY_DOSSIER = ATTACHMENTS + "/dossier/{dossierId}";
    public static final String ATTACHMENTS_DOWNLOAD   = ATTACHMENTS + "/{attachmentId}/download";

    // ── Export PDF ────────────────────────────────────────────

    public static final String PDF_DOSSIER = BASE + "/pdf/dossier/{id}";

    // ── Profil agent ──────────────────────────────────────────

    public static final String PROFILE          = BASE + "/profile";
    public static final String PROFILE_PASSWORD = BASE + "/profile/password";
}