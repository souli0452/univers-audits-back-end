package gov.bf.ascelc.univers_audits.security.access;

import gov.bf.ascelc.univers_audits.security.access.EndpointAccess.Kind;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produit la migration Liquibase 028 : le catalogue de permissions et les attributions de départ, déduits des
 * contrôles d'accès qui existaient avant la bascule (instantané) et de la table point d'entrée → permission.
 * Chaque rôle reçoit ainsi exactement les accès qu'il avait.
 */
final class PermissionMigrationSql {

    /** Les 20 permissions indicatives de la migration 004 : désactivées, jamais supprimées. */
    static final List<String> ANCIENNES_PERMISSIONS = List.of(
            "VOIR_DOSSIER", "CREER_DOSSIER", "MODIFIER_DOSSIER", "CHANGER_STATUT_DOSSIER", "SUPPRIMER_DOSSIER",
            "EXPORTER_DOSSIER", "VOIR_INVESTIGATION", "CREER_INVESTIGATION", "MODIFIER_INVESTIGATION",
            "VALIDER_INVESTIGATION", "VOIR_RAPPORT", "CREER_RAPPORT", "VALIDER_RAPPORT", "VOIR_AGENT",
            "CREER_AGENT", "MODIFIER_AGENT", "DESACTIVER_AGENT", "GERER_ROLES", "VOIR_STATISTIQUES",
            "EXPORTER_STATISTIQUES");

    /**
     * Permissions vérifiées dans le code (services, gardes) et non sur un point d'entrée : l'instantané ne peut pas
     * les déduire, leurs attributions de départ sont donc déclarées ici, à l'identique du comportement d'avant.
     */
    static final Map<AppPermission, Set<String>> ATTRIBUTIONS_COMPLEMENTAIRES = Map.of(
            AppPermission.DOSSIER_VOIR_TOUS, Set.of("ADMIN_DDIC", "CGE", "CGEA"),
            // Décision du 2026-11-15 : l'administrateur voit tout, identité des lanceurs d'alerte protégés comprise.
            AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER, Set.of("ADMIN_DDIC", "CGE", "CGEA"),
            // Annuaire des agents actifs (nom, matricule, courriel) pour les écrans métier : tous les rôles métier,
            // pas la communication (DCP), qui ne doit voir que des statistiques.
            AppPermission.AGENT_ANNUAIRE_VOIR, Set.of("ADMIN_DDIC", "CGE", "CGEA", "CONSEILLER_JURIDIQUE",
                    "CONTROLEUR_ETAT", "MEMBRE_CTADP", "AGENT_BRPD"),
            // Répartition d'un dossier par le chef du département (point d'entrée créé après l'instantané) :
            // l'administrateur la garde ; les chefs la reçoivent par la migration 032.
            AppPermission.FICHE_AFFECTATION_REPARTIR, Set.of("ADMIN_DDIC"));

    private static final List<String> ORDRE_DES_ROLES = List.of(
            "ADMIN_DDIC", "CGE", "CGEA", "CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT", "MEMBRE_CTADP", "AGENT_BRPD", "DCP");

    private static final Pattern CLE_INSEREE = Pattern.compile("\\(gen_random_uuid\\(\\), '([A-Z_]+)', ");

    private PermissionMigrationSql() {
    }

    /**
     * Rôles qui reçoivent chaque permission, déduits de l'instantané. Lève une exception si une permission couvre
     * des points d'entrée dont les rôles diffèrent : le catalogue serait alors trop grossier.
     */
    static Map<AppPermission, SortedSet<String>> rolesParPermission(List<EndpointAccess> golden,
                                                                    Map<String, AppPermission> table) {
        Map<AppPermission, SortedSet<String>> resultat = new EnumMap<>(AppPermission.class);
        Map<AppPermission, String> temoin = new EnumMap<>(AppPermission.class);
        for (EndpointAccess a : golden) {
            AppPermission permission = table.get(AccessFixtures.cle(a));
            if (permission == null || a.kind() != Kind.ROLES) {
                continue;
            }
            SortedSet<String> deja = resultat.get(permission);
            if (deja == null) {
                resultat.put(permission, new TreeSet<>(a.roles()));
                temoin.put(permission, a.handler());
            } else if (!deja.equals(a.roles())) {
                throw new IllegalStateException("Catalogue trop grossier : " + permission.key() + " couvre "
                        + temoin.get(permission) + " (" + deja + ") et " + a.handler() + " (" + a.roles()
                        + ") — scinder cette permission");
            }
        }
        ATTRIBUTIONS_COMPLEMENTAIRES.forEach((permission, roles) -> {
            if (resultat.containsKey(permission)) {
                throw new IllegalStateException(permission.key()
                        + " a un point d'entrée : retirer son attribution complémentaire");
            }
            resultat.put(permission, new TreeSet<>(roles));
        });
        return resultat;
    }

    static String generer(List<EndpointAccess> golden, Map<String, AppPermission> table) {
        Map<AppPermission, SortedSet<String>> parPermission = rolesParPermission(golden, table);

        StringBuilder sql = new StringBuilder();
        sql.append("--liquibase formatted sql\n");
        sql.append("--changeset dev:028-permissions-fines\n\n");
        sql.append("-- FICHIER GÉNÉRÉ — ne pas modifier à la main (voir PermissionMigrationSql dans les tests).\n");
        sql.append("-- Catalogue des permissions fines (une par action métier) et attributions de départ :\n");
        sql.append("-- chaque rôle reçoit exactement les accès que le code lui donnait avant la bascule.\n\n");

        sql.append("-- Les 20 permissions indicatives de la migration 004 ne commandaient aucun accès : désactivées,\n");
        sql.append("-- conservées pour l'historique.\n");
        sql.append("UPDATE permission SET active = FALSE WHERE permission_key IN (\n    ");
        sql.append(String.join(", ", ANCIENNES_PERMISSIONS.stream().map(PermissionMigrationSql::quote).toList()));
        sql.append(");\n\n");

        sql.append("INSERT INTO permission (id, permission_key, label, category, active, created_at) VALUES\n");
        List<AppPermission> catalogue = new ArrayList<>(List.of(AppPermission.values()));
        catalogue.sort(Comparator.comparing(AppPermission::category).thenComparing(AppPermission::key));
        for (int i = 0; i < catalogue.size(); i++) {
            AppPermission p = catalogue.get(i);
            sql.append("    (gen_random_uuid(), ").append(quote(p.key())).append(", ").append(quote(p.label()))
                    .append(", ").append(quote(p.category())).append(", TRUE, now())")
                    .append(i < catalogue.size() - 1 ? ",\n" : "\n");
        }
        sql.append("ON CONFLICT (permission_key) DO UPDATE\n");
        sql.append("    SET label = EXCLUDED.label, category = EXCLUDED.category, active = TRUE;\n");

        Map<String, List<AppPermission>> parRole = new java.util.TreeMap<>(rolesComparator());
        parPermission.forEach((p, roles) -> roles.forEach(r -> parRole.computeIfAbsent(r, k -> new ArrayList<>()).add(p)));
        for (Map.Entry<String, List<AppPermission>> e : parRole.entrySet()) {
            List<AppPermission> permissions = e.getValue();
            permissions.sort(Comparator.comparing(AppPermission::category).thenComparing(AppPermission::key));
            sql.append("\n-- ").append(e.getKey()).append(" : ").append(permissions.size()).append(" permissions\n");
            sql.append("INSERT INTO role_permission (role_id, permission_id)\n");
            sql.append("SELECT r.id, p.id FROM role_definition r JOIN permission p ON p.permission_key IN (\n    ");
            sql.append(String.join(",\n    ", permissions.stream().map(p -> quote(p.key())).toList()));
            sql.append(")\nWHERE r.role_key = ").append(quote(e.getKey())).append("\nON CONFLICT DO NOTHING;\n");
        }
        return sql.toString();
    }

    /** Clés insérées dans la table {@code permission} par le script. */
    static List<String> clesInserees(String sql) {
        List<String> cles = new ArrayList<>();
        Matcher m = CLE_INSEREE.matcher(sql);
        while (m.find()) {
            cles.add(m.group(1));
        }
        return cles;
    }

    private static Comparator<String> rolesComparator() {
        return Comparator.<String>comparingInt(r -> {
            int i = ORDRE_DES_ROLES.indexOf(r);
            return i < 0 ? ORDRE_DES_ROLES.size() : i;
        }).thenComparing(Comparator.naturalOrder());
    }

    private static String quote(String texte) {
        return "'" + texte.replace("'", "''") + "'";
    }
}
