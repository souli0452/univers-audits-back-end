package gov.bf.ascelc.univers_audits.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucun secret ne doit vivre dans les fichiers versionnés (audit du 2026-11-24 : une clé d'API se retrouvait dans
 * l'historique git). Les valeurs viennent de l'environnement (.env, ignoré par git).
 */
class SecretsDansLeCodeTest {

    /** Clés d'API Africa's Talking (« atsk_ » + 60 caractères hexadécimaux ou plus). */
    private static final Pattern CLE_AFRICAS_TALKING = Pattern.compile("atsk_[0-9a-f]{16,}");

    /** Secret écrit entre guillemets dans un script : $secret = "abcdef…" (16 caractères ou plus). */
    private static final Pattern SECRET_EN_DUR_PS1 =
            Pattern.compile("(?i)\\$\\w*secret\\w*\\s*=\\s*\"[A-Za-z0-9_\\-]{16,}\"");

    private static List<Path> fichiers(String... racines) throws IOException {
        try (Stream<Path> s = Stream.of(racines).map(Path::of).filter(Files::exists).flatMap(r -> {
            try { return Files.walk(r); } catch (IOException e) { throw new RuntimeException(e); }
        })) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    private static String lire(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);   // accepte tout octet (images, polices du thème)
    }

    @Test
    void aucuneCleAfricasTalkingDansLesFichiersDeConfiguration() throws IOException {
        List<Path> aVerifier = fichiers("src/main/resources", "src/test/resources", "keycloak", "docs");
        aVerifier = new java.util.ArrayList<>(aVerifier);
        for (String f : List.of("docker-compose.yml", "docker-compose.prod.yml", "test-asce.ps1", "Test-ASCELC.ps1")) {
            if (Files.exists(Path.of(f))) aVerifier.add(Path.of(f));
        }
        for (Path p : aVerifier) {
            assertThat(CLE_AFRICAS_TALKING.matcher(lire(p)).find())
                    .as("clé Africa's Talking en clair dans %s", p).isFalse();
        }
    }

    @Test
    void lesScriptsDEssaiNeContiennentPasDeSecretEnDur() throws IOException {
        for (String f : List.of("test-asce.ps1", "Test-ASCELC.ps1")) {
            Path p = Path.of(f);
            if (!Files.exists(p)) continue;
            assertThat(SECRET_EN_DUR_PS1.matcher(lire(p)).find())
                    .as("secret écrit en dur dans %s : le lire dans $env:KEYCLOAK_CLIENT_SECRET", f).isFalse();
        }
    }
}
