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

    // ── Investigations ────────────────────────────────────────

    public static final String INVESTIGATIONS            = BASE + "/investigations";

    // ── Séances CTADP ─────────────────────────────────────────

    public static final String SEANCES_CTADP = BASE + "/seances-ctadp";

    // ── Notifications ─────────────────────────────────────────

    public static final String NOTIFICATIONS            = BASE + "/notifications";

    // ── Statistiques ──────────────────────────────────────────

    public static final String STATS           = BASE + "/stats";

    // ── Informations préoccupantes ────────────────────────────

    public static final String INFORMATIONS_PREOCCUPANTES = BASE + "/informations-preoccupantes";

    // ── Agents ────────────────────────────────────────────────

    public static final String AGENTS              = BASE + "/agents";

    // ── Configuration — référentiels ──────────────────────────

    public static final String CONFIG                 = BASE + "/config";

    // ── Pièces jointes ────────────────────────────────────────

    public static final String ATTACHMENTS            = BASE + "/attachments";

    // ── Export PDF ────────────────────────────────────────────

//    public static final String PDF_DOSSIER = BASE + "/pdf/dossier/{id}";

}