package gov.bf.ascelc.univers_audits.security.permission;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogue fermé des permissions de l'application : une constante par action métier.
 *
 * <p>Le code ne demande jamais « as-tu le rôle X ? » mais « as-tu la permission Y ? » (voir {@link HasPermission}).
 * Les rôles, eux, sont des ensembles de permissions stockés en base et modifiables sans redéploiement ; cette
 * liste ne change que lorsqu'une nouvelle action métier apparaît dans le code.
 *
 * <p>Une permission {@linkplain #protegee() protégée} est conservée en permanence par le rôle d'administration,
 * pour qu'une erreur de configuration ne puisse pas l'enfermer hors du système.
 *
 * <p>Pour ajouter une permission : ajouter la constante ici, la protéger sur l'endpoint avec
 * {@code @HasPermission}, puis écrire la migration Liquibase qui l'insère et l'attribue aux rôles voulus.
 */
public enum AppPermission {

    // ─── Dossiers ──────────────────────────────────────────────────────────────
    DOSSIER_CLORE("Dossiers", "Clore un dossier"),
    DOSSIER_CONFIDENTIALITE_MODIFIER("Dossiers", "Modifier la confidentialité d'un dossier"),
    DOSSIER_DEMANDER_COMPLEMENT("Dossiers", "Demander un complément d'information"),
    DOSSIER_ENREGISTRER("Dossiers", "Enregistrer un dossier"),
    DOSSIER_MES_DOSSIERS_VOIR("Dossiers", "Consulter ses dossiers"),
    DOSSIER_MODIFIER("Dossiers", "Modifier un dossier"),
    DOSSIER_PRIORISER("Dossiers", "Définir la priorité d'un dossier"),
    DOSSIER_SOUMETTRE_CTADP("Dossiers", "Soumettre un dossier à la CTADP"),
    DOSSIER_STATUER_RECEVABILITE("Dossiers", "Statuer sur la recevabilité d'un dossier"),
    DOSSIER_TRANSFERER("Dossiers", "Transférer un dossier"),
    DOSSIER_VOIR("Dossiers", "Consulter les dossiers"),
    DOSSIER_VOIR_TOUS("Dossiers", "Consulter tous les dossiers, y compris les dossiers confidentiels"),
    FICHE_AFFECTATION_AFFECTER("Dossiers", "Affecter un dossier"),
    FICHE_AFFECTATION_CREER("Dossiers", "Créer une fiche d'affectation"),
    HABILITATION_ACCORDER("Dossiers", "Habiliter un agent sur un dossier"),
    HABILITATION_RETIRER("Dossiers", "Retirer une habilitation"),
    HABILITATION_VOIR("Dossiers", "Consulter les habilitations sur les dossiers"),
    LANCEUR_ALERTE_PROTEGE_TRAITER("Dossiers", "Voir et traiter les dossiers de lanceurs d'alerte protégés"),
    OBSERVATION_AJOUTER("Dossiers", "Ajouter une observation"),
    OBSERVATION_VOIR("Dossiers", "Consulter les observations"),
    PARTIE_VISEE_GERER("Dossiers", "Enregistrer ou modifier une partie visée"),
    PARTIE_VISEE_SUPPRIMER("Dossiers", "Supprimer une partie visée"),
    PARTIE_VISEE_VOIR("Dossiers", "Consulter les parties visées"),
    PIECE_JOINTE_RECLASSER("Dossiers", "Reclasser une pièce jointe"),
    PIECE_JOINTE_SUPPRIMER("Dossiers", "Supprimer une pièce jointe"),
    PIECE_JOINTE_VOIR("Dossiers", "Consulter les pièces jointes"),
    TEMOIN_GERER("Dossiers", "Enregistrer ou modifier un témoin"),
    TEMOIN_SUPPRIMER("Dossiers", "Supprimer un témoin"),
    TEMOIN_VOIR("Dossiers", "Consulter les témoins"),

    // ─── Réception ─────────────────────────────────────────────────────────────
    INFO_PREOCCUPANTE_AUTOSAISIR("Réception", "Déclencher une auto-saisine"),
    INFO_PREOCCUPANTE_CREER("Réception", "Enregistrer une information préoccupante"),
    INFO_PREOCCUPANTE_TRAITER("Réception", "Classer ou rattacher une information préoccupante"),
    INFO_PREOCCUPANTE_VOIR("Réception", "Consulter les informations préoccupantes"),

    // ─── Étude d'opportunité et CTADP ──────────────────────────────────────────
    ETUDE_OPPORTUNITE_REDIGER("Étude d'opportunité et CTADP", "Rédiger l'étude d'opportunité"),
    ETUDE_OPPORTUNITE_VOIR("Étude d'opportunité et CTADP", "Consulter l'étude d'opportunité"),
    SEANCE_CTADP_PREPARER("Étude d'opportunité et CTADP", "Préparer une séance CTADP"),
    SEANCE_CTADP_RECOMMANDER("Étude d'opportunité et CTADP", "Enregistrer la recommandation de la CTADP"),
    SEANCE_CTADP_TENIR("Étude d'opportunité et CTADP", "Tenir une séance CTADP"),
    SEANCE_CTADP_VOIR("Étude d'opportunité et CTADP", "Consulter les séances CTADP"),

    // ─── Investigations ────────────────────────────────────────────────────────
    CHECKLIST_COCHER("Investigations", "Cocher la checklist du dossier"),
    CHECKLIST_VOIR("Investigations", "Consulter la checklist du dossier"),
    DEMANDE_DOCUMENTS_GERER("Investigations", "Gérer les demandes de documents"),
    DEMANDE_DOCUMENTS_VOIR("Investigations", "Consulter les demandes de documents"),
    INCIDENT_DECLARER("Investigations", "Déclarer un incident"),
    INCIDENT_VOIR("Investigations", "Consulter les incidents"),
    INVENTAIRE_PIECES_VOIR("Investigations", "Consulter l'inventaire des pièces"),
    INVESTIGATION_AVIS_JURIDIQUE_DONNER("Investigations", "Donner l'avis juridique"),
    INVESTIGATION_CGEA_STATUER("Investigations", "Approuver ou rejeter (CGEA)"),
    INVESTIGATION_CGE_STATUER("Investigations", "Approuver ou rejeter (CGE)"),
    INVESTIGATION_DEI_STATUER("Investigations", "Approuver ou rejeter (DEI)"),
    INVESTIGATION_DELAI_PROROGER("Investigations", "Proroger le délai d'une investigation"),
    INVESTIGATION_DEMARRER("Investigations", "Démarrer une investigation"),
    INVESTIGATION_EQUIPE_GERER("Investigations", "Composer l'équipe d'investigation"),
    INVESTIGATION_MANDAT_DELIVRER("Investigations", "Délivrer le mandat d'investigation"),
    INVESTIGATION_OUVRIR("Investigations", "Ouvrir une investigation"),
    INVESTIGATION_PLAN_SOUMETTRE("Investigations", "Soumettre ou réviser le plan d'investigation"),
    INVESTIGATION_PLAN_VALIDER("Investigations", "Valider le plan d'investigation"),
    INVESTIGATION_RAPPORT_SOUMETTRE("Investigations", "Soumettre le rapport d'investigation"),
    INVESTIGATION_RAPPORT_VOIR("Investigations", "Consulter le rapport des investigations"),
    INVESTIGATION_RETARDS_VOIR("Investigations", "Consulter les investigations en retard"),
    INVESTIGATION_SUSPENDRE("Investigations", "Suspendre ou reprendre une investigation"),
    INVESTIGATION_TOUTES_VOIR("Investigations", "Consulter toutes les investigations"),
    INVESTIGATION_URGENCE_DEMANDER("Investigations", "Demander la procédure d'urgence"),
    INVESTIGATION_URGENCE_STATUER("Investigations", "Statuer sur la procédure d'urgence"),
    INVESTIGATION_VOIR("Investigations", "Consulter une investigation"),
    MESURE_CONSERVATOIRE_DECLARER("Investigations", "Déclarer une mesure conservatoire"),
    RAPPORT_ENQUETE_REDIGER("Investigations", "Rédiger le rapport d'enquête"),
    RAPPORT_ENQUETE_VOIR("Investigations", "Consulter le rapport d'enquête"),
    SECTION_DOSSIER_TRAVAIL_GERER("Investigations", "Alimenter le dossier de travail"),
    SECTION_DOSSIER_TRAVAIL_VOIR("Investigations", "Consulter le dossier de travail"),

    // ─── Auditions et visites ──────────────────────────────────────────────────
    AUDITION_CONDUIRE("Auditions et visites", "Conduire une audition"),
    AUDITION_PLANIFIER("Auditions et visites", "Planifier ou annuler une audition"),
    AUDITION_PV_REDIGER("Auditions et visites", "Rédiger le procès-verbal d'audition"),
    AUDITION_VOIR("Auditions et visites", "Consulter les auditions"),
    REGISTRE_AUDITIONS_VOIR("Auditions et visites", "Consulter le registre des auditions"),
    VISITE_TERRAIN_CONDUIRE("Auditions et visites", "Conduire une visite de terrain"),
    VISITE_TERRAIN_PLANIFIER("Auditions et visites", "Planifier ou annuler une visite de terrain"),
    VISITE_TERRAIN_VOIR("Auditions et visites", "Consulter les visites de terrain"),

    // ─── Suites ────────────────────────────────────────────────────────────────
    FICHE_RETEX_REDIGER("Suites", "Rédiger la fiche de retour d'expérience"),
    FICHE_RETEX_VOIR("Suites", "Consulter la fiche de retour d'expérience"),
    LECON_PUBLIER("Suites", "Publier une leçon à partager"),
    MISSION_SUIVI_AJOUTER("Suites", "Ajouter une mission de suivi"),
    MISSION_SUIVI_VOIR("Suites", "Consulter les missions de suivi"),
    PARTIE_CIVILE_CONSTITUER("Suites", "Constituer la partie civile"),
    PARTIE_CIVILE_VOIR("Suites", "Consulter la constitution de partie civile"),
    PLAN_ACTIONS_GERER("Suites", "Gérer le plan d'actions"),
    PLAN_ACTIONS_VOIR("Suites", "Consulter le plan d'actions"),
    PROCEDURE_PENALE_AJOUTER("Suites", "Ajouter au suivi de la procédure pénale"),
    PROCEDURE_PENALE_VOIR("Suites", "Consulter le suivi de la procédure pénale"),
    REQUETE_PARQUET_REDIGER("Suites", "Rédiger la requête au parquet"),
    REQUETE_PARQUET_VOIR("Suites", "Consulter la requête au parquet"),
    TRANSMISSION_AUTORITE_CREER("Suites", "Transmettre un dossier à l'autorité"),
    TRANSMISSION_AUTORITE_RELANCER("Suites", "Relancer l'autorité"),
    TRANSMISSION_AUTORITE_VOIR("Suites", "Consulter la transmission à l'autorité"),

    // ─── Documents ─────────────────────────────────────────────────────────────
    PDF_CONVOCATION_CTADP_EXPORTER("Documents", "Exporter la convocation CTADP"),
    PDF_LETTRE_INFORMATION_EXPORTER("Documents", "Exporter la lettre d'information"),
    PDF_QUITUS_EXPORTER("Documents", "Exporter le quitus"),

    // ─── Statistiques ──────────────────────────────────────────────────────────
    STATISTIQUE_DEPASSEMENTS_VOIR("Statistiques", "Consulter les dépassements de délais"),
    STATISTIQUE_EXPORTER("Statistiques", "Exporter les statistiques annuelles"),
    STATISTIQUE_GLOBALE_VOIR("Statistiques", "Consulter les statistiques globales"),

    // ─── Notifications ─────────────────────────────────────────────────────────
    NOTIFICATION_DOSSIER_VOIR("Notifications", "Consulter les notifications d'un dossier"),
    NOTIFICATION_FILE_GERER("Notifications", "Gérer les notifications en attente"),
    NOTIFICATION_FILE_VOIR("Notifications", "Consulter les notifications en attente"),

    // ─── Paramétrage ───────────────────────────────────────────────────────────
    CHECKLIST_POINT_GERER("Paramétrage", "Gérer les points de la checklist"),
    INDICE_FRAUDE_GERER("Paramétrage", "Gérer les indices de fraude"),
    JOUR_FERIE_GERER("Paramétrage", "Gérer les jours fériés"),
    PARAMETRE_DELAI_GERER("Paramétrage", "Gérer les paramètres de délais"),
    PORTAIL_CONFIG_MODIFIER("Paramétrage", "Modifier les paramètres du portail"),
    PORTAIL_CONFIG_VOIR("Paramétrage", "Consulter les paramètres du portail"),
    PORTAIL_IMAGES_GERER("Paramétrage", "Gérer les images du portail"),
    TYPE_INFRACTION_GERER("Paramétrage", "Gérer les types d'infraction"),

    // ─── Administration ────────────────────────────────────────────────────────
    ACCES_MATRICE_VOIR("Administration", "Consulter les accès par rôle", true),
    AGENT_ACTIVER("Administration", "Activer ou désactiver un agent"),
    AGENT_CREER("Administration", "Créer un agent"),
    AGENT_MODIFIER("Administration", "Modifier un agent"),
    AGENT_ROLES_ATTRIBUER("Administration", "Attribuer des rôles à un agent", true),
    AGENT_VOIR("Administration", "Consulter les agents"),
    ROLE_CREER("Administration", "Créer un rôle"),
    ROLE_MODIFIER("Administration", "Modifier les permissions d'un rôle", true),
    ROLE_SUPPRIMER("Administration", "Supprimer un rôle"),
    ROLE_VOIR("Administration", "Consulter les rôles et leurs permissions", true),

    // ─── Audit ─────────────────────────────────────────────────────────────────
    AUDIT_JOURNAL_VOIR("Audit", "Consulter le journal d'audit", true),
    ;

    /** Préfixe des autorités Spring correspondantes (« PERM_DOSSIER_VOIR »). */
    public static final String AUTHORITY_PREFIX = "PERM_";

    private static final Map<String, AppPermission> PAR_CLE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(AppPermission::key, Function.identity()));

    private final String category;
    private final String label;
    private final boolean protegee;

    AppPermission(String category, String label) {
        this(category, label, false);
    }

    AppPermission(String category, String label, boolean protegee) {
        this.category = category;
        this.label = label;
        this.protegee = protegee;
    }

    /** Clé stockée en base (colonne {@code permission.permission_key}). */
    public String key() {
        return name();
    }

    /** Autorité Spring Security accordée à un utilisateur qui détient cette permission. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    /** Vrai si le rôle d'administration ne peut pas la perdre. */
    public boolean protegee() {
        return protegee;
    }

    /** Retrouve une permission par sa clé ; vide si la clé est nulle ou inconnue. */
    public static Optional<AppPermission> of(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(PAR_CLE.get(key));
    }
}
