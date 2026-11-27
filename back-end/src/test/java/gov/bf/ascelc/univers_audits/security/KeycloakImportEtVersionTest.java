package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le fichier d'import du royaume est un modèle versionné : il ne porte aucune clé ni aucun mot de passe (Keycloak génère
 * ses clés au premier démarrage), il donne au serveur le droit exigé par Keycloak 26, et la version de Keycloak est la
 * même partout.
 */
class KeycloakImportEtVersionTest {

    private static final String VERSION = "26.8.2";
    private static JsonNode royaume;

    @BeforeAll
    static void charger() throws IOException {
        royaume = new ObjectMapper().readTree(new File("keycloak/import/realm-export.json"));
    }

    private static List<JsonNode> liste(JsonNode noeud) {
        return noeud == null ? List.of() : StreamSupport.stream(noeud.spliterator(), false).toList();
    }

    @Test
    void aucuneCleDeRoyaume() {
        assertThat(royaume.path("components").has("org.keycloak.keys.KeyProvider")).isFalse();
        assertThat(royaume.findValues("privateKey")).isEmpty();
    }

    @Test
    void aucunMotDePasseNiEmpreinte() {
        assertThat(royaume.path("smtpServer").has("password")).isFalse();
        assertThat(liste(royaume.get("users"))).allSatisfy(u -> assertThat(liste(u.get("credentials"))).isEmpty());
        assertThat(royaume.findValues("secretData")).isEmpty();
    }

    @Test
    void aucunSecretDeClientEcritEnClair() {
        assertThat(liste(royaume.get("clients")))
                .allSatisfy(c -> assertThat(c.path("secret").asText("")).matches("^$|^\\$\\{.*}$|^\\*+$"));
    }

    @Test
    void leServeurAPorteeExigeeParKeycloak26() {
        JsonNode serveur = liste(royaume.get("clients")).stream()
                .filter(c -> "asce-lc-backend".equals(c.path("clientId").asText())).findFirst().orElseThrow();

        assertThat(liste(serveur.get("defaultClientScopes"))).extracting(JsonNode::asText).contains("kc-admin-api");
        assertThat(liste(royaume.get("clientScopes"))).extracting(s -> s.path("name").asText()).contains("kc-admin-api");
    }

    @Test
    void memeVersionDeKeycloakPartout() throws IOException {
        for (String fichier : List.of("docker-compose.yml", "docker-compose.prod.yml",
                "docs/deploiement/2026-11-27/docker-compose.override.yml", "scripts/securite/zap-actif.sh")) {
            Matcher m = Pattern.compile("quay\\.io/keycloak/keycloak:([0-9.]+)")
                    .matcher(Files.readString(Path.of(fichier), StandardCharsets.UTF_8));
            List<String> versions = new ArrayList<>();
            while (m.find()) versions.add(m.group(1));
            assertThat(versions).as(fichier).isNotEmpty().allMatch(VERSION::equals);
        }
    }

    @Test
    void pasDHabillageDuCompte_sonModeleNexistePlusEnVersion26() {
        assertThat(new File("keycloak/themes/asce-lc/account")).doesNotExist();
    }
}
