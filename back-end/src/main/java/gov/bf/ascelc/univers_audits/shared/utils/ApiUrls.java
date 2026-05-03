package gov.bf.ascelc.univers_audits.shared.utils;

public final class ApiUrls {


    private ApiUrls() {}
    public static final String API     = "/api";
    public static final String VERSION = "/v1";
    public static final String BASE    = API + VERSION;

    // Dossiers

    public static final String DOSSIERS              = BASE + "/dossiers";
    public static final String DOSSIERS_BY_ID        = DOSSIERS + "/{id}";

    // Endpoints publics — accessibles sans authentification
    public static final String DOSSIERS_PUBLIC       = DOSSIERS + "/public";
    public static final String DOSSIERS_TRACK        = DOSSIERS_PUBLIC + "/track/{accessCode}";
    public static final String DOSSIERS_SUBMIT       = DOSSIERS_PUBLIC + "/submit";

    // Filtres et recherche
    public static final String DOSSIERS_BY_STATUS    = DOSSIERS + "/status/{status}";
    public static final String DOSSIERS_MY           = DOSSIERS + "/my";

    // Transitions de statut
    public static final String DOSSIERS_REGISTER     = DOSSIERS_BY_ID + "/register";
    public static final String DOSSIERS_START_STUDY  = DOSSIERS_BY_ID + "/start-study";
    public static final String DOSSIERS_REQ_COMPL    = DOSSIERS_BY_ID + "/request-complement";
    public static final String DOSSIERS_COMPL_RECV   = DOSSIERS_BY_ID + "/complement-received";
    public static final String DOSSIERS_CTADP        = DOSSIERS_BY_ID + "/submit-ctadp";
    public static final String DOSSIERS_ADMISSIBLE   = DOSSIERS_BY_ID + "/declare-admissible";
    public static final String DOSSIERS_INADMISSIBLE = DOSSIERS_BY_ID + "/declare-inadmissible";
    public static final String DOSSIERS_TRANSFER     = DOSSIERS_BY_ID + "/transfer";
    public static final String DOSSIERS_CLOSE        = DOSSIERS_BY_ID + "/close";

    // Investigations

    public static final String INVESTIGATIONS              = BASE + "/investigations";
    public static final String INVESTIGATIONS_BY_ID        = INVESTIGATIONS + "/{id}";
    public static final String INVESTIGATIONS_OVERDUE      = INVESTIGATIONS + "/overdue";
    public static final String INVESTIGATIONS_BY_DOSSIER   = INVESTIGATIONS + "/dossier/{dossierId}";
    public static final String INVESTIGATIONS_OPEN         = INVESTIGATIONS + "/dossier/{dossierId}/open";

    // Cycle de vie
    public static final String INVESTIGATIONS_START        = INVESTIGATIONS_BY_ID + "/start";
    public static final String INVESTIGATIONS_SUSPEND      = INVESTIGATIONS_BY_ID + "/suspend";
    public static final String INVESTIGATIONS_RESUME       = INVESTIGATIONS_BY_ID + "/resume";
    public static final String INVESTIGATIONS_EXTEND       = INVESTIGATIONS_BY_ID + "/extend-deadline";
    public static final String INVESTIGATIONS_REPORT       = INVESTIGATIONS_BY_ID + "/submit-report";

    // Circuit d'approbation
    public static final String INVESTIGATIONS_APPROVE_DEI  = INVESTIGATIONS_BY_ID + "/approve-dei";
    public static final String INVESTIGATIONS_APPROVE_LEG  = INVESTIGATIONS_BY_ID + "/approve-legal";
    public static final String INVESTIGATIONS_APPROVE_CGE  = INVESTIGATIONS_BY_ID + "/approve-cge";

    // Gestion équipe
    public static final String INVESTIGATIONS_MEMBERS      = INVESTIGATIONS_BY_ID + "/members";
    public static final String INVESTIGATIONS_MEMBER_BY_ID = INVESTIGATIONS_BY_ID + "/members/{agentId}";

    // Notifications

    public static final String NOTIFICATIONS             = BASE + "/notifications";
    public static final String NOTIFICATIONS_BY_ID      = NOTIFICATIONS + "/{id}";
    public static final String NOTIFICATIONS_BY_DOSSIER = NOTIFICATIONS + "/dossier/{dossierId}";
    public static final String NOTIFICATIONS_OVERDUE    = NOTIFICATIONS + "/overdue";
    public static final String NOTIFICATIONS_PENDING    = NOTIFICATIONS + "/pending";
    public static final String NOTIFICATIONS_SEND       = NOTIFICATIONS_BY_ID + "/send";
    public static final String NOTIFICATIONS_CANCEL     = NOTIFICATIONS_BY_ID + "/cancel";
    public static final String NOTIFICATIONS_RETRY      = NOTIFICATIONS_BY_ID + "/retry";

    // Statistiques

    public static final String STATS           = BASE + "/stats";
    public static final String STATS_DASHBOARD = STATS + "/dashboard";
    public static final String STATS_QUARTERLY = STATS + "/quarterly";
    public static final String STATS_ANNUAL    = STATS + "/annual";
    public static final String STATS_PUBLIC = STATS + "/public";

    // Agents

    public static final String AGENTS          = BASE + "/agents";
    public static final String AGENTS_BY_ID    = AGENTS + "/{id}";
    public static final String AGENTS_ME       = AGENTS + "/me";
    public static final String AGENTS_ROLES    = AGENTS_BY_ID + "/roles";

    // Configuration

    public static final String CONFIG                    = BASE + "/config";
    public static final String CONFIG_DEPARTEMENTS       = CONFIG + "/departements";
    public static final String CONFIG_ROLES              = CONFIG + "/roles";
    public static final String CONFIG_TYPES_DECLARANT    = CONFIG + "/types-declarant";
}