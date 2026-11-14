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

    private AccessFixtures() {
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
