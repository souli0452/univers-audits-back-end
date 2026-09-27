package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le portail public affiche le paramètre « hotline_number » (table portal_config). La migration 005 l'a
 * initialisé à l'ancien numéro « 80 00 11 11 » ; le numéro vert institutionnel est
 * {@link AsceLcInstitutionalInfo#NUMERO_VERT}. Une migration doit aligner la base sur cette constante
 * SANS écraser un numéro que l'administration aurait saisi elle-même.
 */
class HotlineNumberMigrationTest {

    private static final String ANCIEN_NUMERO_SEME = "80 00 11 11";

    @Test
    void uneMigrationAligneLeNumeroVertDuPortailSurLaConstanteInstitutionnelle() throws Exception {
        String sql = migrationDuNumeroVert();

        assertThat(sql).contains("UPDATE portal_config");
        assertThat(sql).containsPattern("config_key\\s*=\\s*'hotline_number'");
        assertThat(sql).containsPattern("SET\\s+config_value\\s*=\\s*'" + AsceLcInstitutionalInfo.NUMERO_VERT + "'");
    }

    @Test
    void laMigrationNEcraseQueLAncienNumeroSeme() throws Exception {
        String sql = migrationDuNumeroVert();

        assertThat(sql)
                .as("la mise à jour doit être conditionnée à l'ancien numéro seedé, pour respecter une saisie de l'administration")
                .containsPattern("AND\\s+config_value\\s*=\\s*'" + ANCIEN_NUMERO_SEME + "'");
    }

    @Test
    void laMigrationIncrementeLaVersionPourLeVerrouillageOptimiste() throws Exception {
        assertThat(migrationDuNumeroVert()).containsPattern("version\\s*=\\s*COALESCE\\(version,\\s*0\\)\\s*\\+\\s*1");
    }

    private static String migrationDuNumeroVert() throws Exception {
        Path dir = Path.of("src/main/resources/db/changelog/migrations");
        try (Stream<Path> fichiers = Files.list(dir)) {
            Path fichier = fichiers
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> lire(p).contains("hotline_number") && lire(p).contains("UPDATE portal_config"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("aucune migration ne met à jour hotline_number"));
            return lire(fichier);
        }
    }

    private static String lire(Path fichier) {
        try {
            return new String(Files.readAllBytes(fichier), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
