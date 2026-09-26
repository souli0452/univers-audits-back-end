package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.enums.ObservationType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou : ajouter une valeur à ObservationType sans élargir le CHECK SQL fait échouer
 * (et annuler) toute transaction qui l'utilise (leçon des migrations 013 et 014).
 */
class ObservationTypeCheckMigrationTest {

    @Test
    void laDerniereContrainteSqlAutoriseChaqueObservationType() throws Exception {
        Path dir = Path.of("src/main/resources/db/changelog/migrations");
        String derniere;
        try (Stream<Path> fichiers = Files.list(dir)) {
            derniere = fichiers
                    .filter(p -> p.toString().endsWith(".sql"))
                    .sorted()
                    .map(ObservationTypeCheckMigrationTest::lire)
                    .filter(sql -> sql.contains("observation_type_check"))
                    .reduce((premier, second) -> second)
                    .orElseThrow();
        }

        for (ObservationType type : ObservationType.values()) {
            assertThat(derniere)
                    .as("la valeur %s doit figurer dans le CHECK observation_type_check", type)
                    .contains("'" + type.name() + "'");
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
