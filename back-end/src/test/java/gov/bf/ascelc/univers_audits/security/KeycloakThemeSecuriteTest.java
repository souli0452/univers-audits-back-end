package gov.bf.ascelc.univers_audits.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les pages de connexion ne chargent rien depuis un site tiers : ni police, ni style, ni script (scan du site complet
 * du 2026-11-25 : Google Fonts sur la page de connexion des agents).
 */
class KeycloakThemeSecuriteTest {

    private static final Path THEME = Path.of("keycloak/themes/asce-lc/login");
    private static final Pattern CHARGEMENT_TIERS =
            Pattern.compile("(<link[^>]+href=|<script[^>]+src=|url\\(|@import\\s+)\\s*[\"']?(https?:)?//[^\"')\\s>]+");

    @Test
    void aucunFichierDuThemeNeChargeUneRessourceExterne() throws IOException {
        List<String> trouves = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(THEME)) {
            for (Path f : fichiers.filter(p -> p.toString().matches(".*\\.(ftl|css|properties)")).toList()) {
                Matcher m = CHARGEMENT_TIERS.matcher(Files.readString(f));
                while (m.find()) {
                    trouves.add(f.getFileName() + " : " + m.group());
                }
            }
        }
        assertThat(trouves).isEmpty();
    }

    @Test
    void lesPolicesDeLaPageDeConnexionSontPresentesDansLeTheme() {
        for (String graisse : List.of("Regular", "Medium", "SemiBold", "Bold")) {
            assertThat(THEME.resolve("resources/fonts/lato/Lato-" + graisse + ".woff2")).exists();
        }
    }
}
