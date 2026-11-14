package gov.bf.ascelc.univers_audits.security.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Instantané des contrôles d'accès actuels de tous les points d'entrée (permission portée par chacun). Un point
 * d'entrée ajouté, retiré ou dont le contrôle change fait échouer ce test : la décision doit alors être prise
 * consciemment, puis l'instantané
 * régénéré avec {@code -Dgolden.update=true} (puis relancer le test, le fichier n'étant copié dans le classpath
 * qu'à la compilation suivante) et relu dans le diff.
 *
 * <p>Ne pas confondre avec {@code access-golden-avant.json} : l'état d'avant la bascule vers les permissions,
 * figé pour toujours, qui sert de référence à {@link AccessEquivalenceIntegrationTest}.
 */
@SpringBootTest
class AccessGoldenIntegrationTest {

    private static final String RESSOURCE = "/security/access-courant.json";
    private static final Path FICHIER = Path.of("src/test/resources/security/access-courant.json");

    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    private EndpointAccessCollector collector;

    @Test
    void instantane_correspond_au_code() throws IOException {
        List<EndpointAccess> actuel = collector.collect();

        if (Boolean.getBoolean("golden.update")) {
            Files.writeString(FICHIER, json.writeValueAsString(actuel) + System.lineSeparator());
        }

        List<EndpointAccess> attendu = lireInstantane();
        Set<String> lignesAttendues = lignes(attendu);
        Set<String> lignesActuelles = lignes(actuel);

        Set<String> disparus = new LinkedHashSet<>(lignesAttendues);
        disparus.removeAll(lignesActuelles);
        Set<String> apparus = new LinkedHashSet<>(lignesActuelles);
        apparus.removeAll(lignesAttendues);

        assertThat(disparus).as("contrôles présents dans l'instantané mais absents du code").isEmpty();
        assertThat(apparus).as("contrôles présents dans le code mais absents de l'instantané").isEmpty();
        assertThat(actuel).hasSameSizeAs(attendu);
    }

    @Test
    void chaque_point_dentree_est_identifie_de_facon_unique() {
        List<String> cles = collector.collect().stream()
                .map(a -> a.handler() + " " + a.httpMethods()).toList();
        assertThat(cles).doesNotHaveDuplicates();
    }

    private List<EndpointAccess> lireInstantane() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(RESSOURCE)) {
            assertThat(in).as("instantané " + RESSOURCE + " (le générer avec -Dgolden.update=true)").isNotNull();
            return json.readValue(in, new TypeReference<List<EndpointAccess>>() { });
        }
    }

    private static Set<String> lignes(List<EndpointAccess> acces) {
        Set<String> lignes = new LinkedHashSet<>();
        for (EndpointAccess a : acces) {
            lignes.add(a.handler() + " " + a.httpMethods() + " " + a.kind() + " " + a.roles() + " " + a.expression());
        }
        return lignes;
    }
}
