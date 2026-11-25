package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Réglages de sécurité du royaume Keycloak importé au démarrage (keycloak/import/realm-export.json), exigés par l'audit
 * du 2026-11-24 : journal des connexions, flux d'authentification de l'écran, protections contre la force brute.
 * (Un royaume déjà créé n'est pas modifié par un nouvel import : voir le guide de déploiement pour le réglage en console.)
 */
class KeycloakRealmSecuriteTest {

    private static JsonNode royaume;

    @BeforeAll
    static void charger() throws Exception {
        royaume = new ObjectMapper().readTree(new File("keycloak/import/realm-export.json"));
    }

    private JsonNode client(String id) {
        return StreamSupport.stream(royaume.get("clients").spliterator(), false)
                .filter(c -> id.equals(c.get("clientId").asText())).findFirst().orElseThrow();
    }

    private Set<String> evenements() {
        Set<String> types = new HashSet<>();
        royaume.get("enabledEventTypes").forEach(t -> types.add(t.asText()));
        return types;
    }

    @Test
    void lesEvenementsDeConnexionSontJournalises() {
        assertThat(royaume.get("eventsEnabled").asBoolean()).isTrue();
        assertThat(evenements()).contains("LOGIN", "LOGIN_ERROR", "LOGOUT", "UPDATE_PASSWORD", "UPDATE_TOTP",
                "RESET_PASSWORD", "USER_DISABLED_BY_TEMPORARY_LOCKOUT");
    }

    @Test
    void lesEvenementsSontConservesAuMoinsTroisMois() {
        assertThat(royaume.get("eventsExpiration").asLong()).isGreaterThanOrEqualTo(90L * 24 * 3600);
    }

    @Test
    void lesActionsDAdministrationSontJournalisees_sansLeContenuDesIdentifiants() {
        assertThat(royaume.get("adminEventsEnabled").asBoolean()).isTrue();
        // le détail d'une opération peut contenir un mot de passe saisi : il n'est pas conservé
        assertThat(royaume.get("adminEventsDetailsEnabled").asBoolean()).isFalse();
    }

    @Test
    void ecranPublic_n_utilise_que_le_code_d_autorisation_avec_PKCE() {
        JsonNode ecran = client("asce-lc-frontend");

        assertThat(ecran.get("publicClient").asBoolean()).isTrue();
        assertThat(ecran.get("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(ecran.get("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(ecran.get("directAccessGrantsEnabled").asBoolean())
                .as("le flux « mot de passe direct » contourne la page de connexion et le code à usage unique").isFalse();
        assertThat(ecran.get("attributes").get("pkce.code.challenge.method").asText()).isEqualTo("S256");
    }

    @Test
    void leServeurResteUnClientConfidentiel() {
        JsonNode serveur = client("asce-lc-backend");

        assertThat(serveur.get("publicClient").asBoolean()).isFalse();
        assertThat(serveur.get("implicitFlowEnabled").asBoolean()).isFalse();
    }

    @Test
    void aucunClientNAutoriseLeFluxImplicite() {
        royaume.get("clients").forEach(c -> assertThat(c.get("implicitFlowEnabled").asBoolean())
                .as("flux implicite sur %s", c.get("clientId").asText()).isFalse());
    }

    @Test
    void lesProtectionsDesComptesRestentEnPlace() {
        assertThat(royaume.get("bruteForceProtected").asBoolean()).isTrue();
        assertThat(royaume.get("failureFactor").asInt()).isLessThanOrEqualTo(3);            // guide ANSSI, R21
        assertThat(royaume.get("passwordPolicy").asText()).contains("length(12)").contains("passwordHistory");
        assertThat(royaume.get("registrationAllowed").asBoolean()).isFalse();
        assertThat(royaume.get("sslRequired").asText()).isIn("external", "all");
    }

    @Test
    void leMotDePasseNeReprendAucuneDonneeDeLAgent() {
        // guide ANSSI, R17
        assertThat(royaume.get("passwordPolicy").asText())
                .contains("notUsername").contains("notContainsUsername").contains("notEmail");
    }

    @Test
    void laSessionExpireApresQuinzeMinutesSansActivite() {
        // guide ANSSI, R29 : l'écran déconnecte lui-même l'agent inactif ; Keycloak couvre l'onglet fermé
        assertThat(royaume.get("ssoSessionIdleTimeout").asInt()).isLessThanOrEqualTo(900);
    }
}
