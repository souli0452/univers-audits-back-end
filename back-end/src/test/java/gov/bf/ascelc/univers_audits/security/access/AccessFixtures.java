package gov.bf.ascelc.univers_audits.security.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Données de test partagées : l'instantané des accès « avant » et la table point d'entrée → permission. */
final class AccessFixtures {

    static final Path GOLDEN_AVANT = Path.of("src/test/resources/security/access-golden-avant.json");
    static final Path TABLE = Path.of("src/test/resources/security/endpoint-permission.csv");

    /**
     * Points d'entrée créés après l'instantané « avant » : ils n'ont pas d'équivalent à comparer, mais chaque ajout
     * doit être déclaré ici pour qu'un point d'entrée ne s'ajoute pas sans qu'on s'en aperçoive.
     */
    static final Set<String> POINTS_DENTREE_AJOUTES = Set.of(
            // Permissions de l'utilisateur connecté, pour l'interface (tout utilisateur connecté).
            "AuthPermissionsController#permissions(Authentication);GET",
            // Répartition d'un dossier par le chef du département (décision du 2026-11-16).
            "FicheAffectationController#repartir(UUID,FicheAffectationRepartitionRequest);POST",
            // Conseillers juridiques proposés à l'affectation : le CGEA n'a plus la route d'administration des agents.
            "AgentController#findConseillersJuridiques();GET",
            // Situation trimestrielle publiée sur le site (mêmes droits que le tableau de bord global).
            "StatistiqueController#getPublicationTrimestrielle(int,int);GET");

    private AccessFixtures() {
    }

    /**
     * Décision du 2026-11-14 : CGE et CGEA ne font que le métier. Les permissions d'administration, de
     * paramétrage et d'audit, ainsi que la file des notifications, leur sont retirées par la migration 029.
     */
    static boolean retireeParLaPolitiqueMetierSeulement(String role, AppPermission permission) {
        boolean directionMetier = role.equals("CGE") || role.equals("CGEA");
        boolean technique = Set.of("Administration", "Paramétrage", "Audit").contains(permission.category())
                || permission == AppPermission.NOTIFICATION_FILE_VOIR
                || permission == AppPermission.NOTIFICATION_FILE_GERER;
        return directionMetier && technique;
    }

    /**
     * Décision du 2026-11-16 : le CGE consulte le registre des auditions, comme le CGEA (le manuel des procédures,
     * processus C, limite l'accès aux dossiers d'investigation au CGE, au CGEA et au chef du DEI). Migration 030.
     */
    static boolean accordeeParLaPolitique(String role, AppPermission permission) {
        return role.equals("CGE") && permission == AppPermission.REGISTRE_AUDITIONS_VOIR;
    }

    /**
     * Décision du 2026-11-16 : le workflow (étape 22) donne l'approbation du rapport de synthèse au « C.DEI », le
     * chef du DEI, pas au CGEA. Le rôle CHEF_DEI la reçoit par la migration 031 ; le CGEA la perd.
     */
    static boolean deplaceeVersLeChefDuDei(String role, AppPermission permission) {
        return role.equals("CGEA")
                && (permission == AppPermission.INVESTIGATION_DEI_STATUER
                || permission == AppPermission.INVESTIGATION_PLAN_VALIDER);
    }

    static List<EndpointAccess> golden() {
        try {
            return new ObjectMapper().readValue(Files.readString(GOLDEN_AVANT), new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Clé d'un point d'entrée : {@code handler} + méthodes HTTP. */
    static String cle(EndpointAccess a) {
        return a.handler() + ";" + a.httpMethods();
    }

    static Set<String> cles(List<EndpointAccess> acces) {
        Set<String> cles = new LinkedHashSet<>();
        acces.forEach(a -> cles.add(cle(a)));
        return cles;
    }

    /** Table {@code handler;httpMethods → permission}. */
    static Map<String, AppPermission> endpointPermissions() {
        try {
            Map<String, AppPermission> table = new LinkedHashMap<>();
            List<String> lignes = Files.readAllLines(TABLE);
            for (String ligne : lignes.subList(1, lignes.size())) {
                if (ligne.isBlank()) {
                    continue;
                }
                String[] c = ligne.split(";", -1);
                AppPermission permission = AppPermission.of(c[2])
                        .orElseThrow(() -> new IllegalStateException("Permission inconnue dans la table : " + c[2]));
                if (table.put(c[0] + ";" + c[1], permission) != null) {
                    throw new IllegalStateException("Point d'entrée en double dans la table : " + ligne);
                }
            }
            return table;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
