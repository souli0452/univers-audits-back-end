package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration 039 : retire les droits de suppléance qu'une base de développement a pu recevoir de l'ancienne version
 * de la 038 (« …-et-suppleance »). On rejoue ici cet état — le CGE et le CGEA dotés des droits de l'agent BRPD —
 * puis la migration : chacun doit retrouver exactement ses droits d'avant. Tout est annulé en fin de test.
 */
@SpringBootTest
@Transactional
class Migration039IntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    /** Droits de saisie de l'agent BRPD que l'ancienne 038 avait donnés au CGE et au CGEA. */
    private static final List<String> DONNES_AU_CGE = List.of("DOSSIER_ENREGISTRER", "DOSSIER_MODIFIER",
            "DOSSIER_MES_DOSSIERS_VOIR", "PARTIE_VISEE_GERER", "PARTIE_VISEE_SUPPRIMER", "TEMOIN_GERER",
            "TEMOIN_SUPPRIMER", "PIECE_JOINTE_RECLASSER", "PIECE_JOINTE_SUPPRIMER", "SECTION_DOSSIER_TRAVAIL_GERER",
            "INFO_PREOCCUPANTE_AUTOSAISIR", "INFO_PREOCCUPANTE_CREER", "INFO_PREOCCUPANTE_TRAITER",
            "INFO_PREOCCUPANTE_VOIR");
    /** Ce que le CGEA avait déjà avant la 038 (migration 028) : à ne jamais retirer. */
    private static final List<String> DEJA_AU_CGEA = List.of("DOSSIER_MES_DOSSIERS_VOIR", "PARTIE_VISEE_SUPPRIMER",
            "TEMOIN_SUPPRIMER", "PIECE_JOINTE_SUPPRIMER");

    private void accorder(String role, List<String> permissions) {
        for (String permission : permissions) {
            jdbc.update("INSERT INTO role_permission (role_id, permission_id) "
                    + "SELECT r.id, p.id FROM role_definition r JOIN permission p ON p.permission_key = ? "
                    + "WHERE r.role_key = ? ON CONFLICT DO NOTHING", permission, role);
        }
    }

    private Set<String> droits(String role, List<String> parmi) {
        return new TreeSet<>(jdbc.queryForList(
                "SELECT p.permission_key FROM role_permission rp JOIN role_definition r ON r.id = rp.role_id "
                        + "JOIN permission p ON p.id = rp.permission_id WHERE r.role_key = ? AND p.permission_key IN ("
                        + String.join(",", parmi.stream().map(x -> "'" + x + "'").toList()) + ")",
                String.class, role));
    }

    private int nombreDeDroits(String role) {
        return jdbc.queryForObject("SELECT count(*) FROM role_permission rp JOIN role_definition r ON r.id = rp.role_id "
                + "WHERE r.role_key = ?", Integer.class, role);
    }

    private void jouerLaMigration() throws Exception {
        Connection connexion = DataSourceUtils.getConnection(dataSource);   // même transaction que le test
        ScriptUtils.executeSqlScript(connexion, new EncodedResource(new ClassPathResource(
                "db/changelog/migrations/039-retrait-suppleance-ancienne-038.sql"), "UTF-8"));
    }

    @Test
    void uneBaseQuiAJoueLAncienne038RetrouveExactementSesDroitsDAvant() throws Exception {
        int cgeAvant = nombreDeDroits("CGE");
        int cgeaAvant = nombreDeDroits("CGEA");
        int brpdAvant = nombreDeDroits("AGENT_BRPD");
        accorder("CGE", DONNES_AU_CGE);
        accorder("CGEA", DONNES_AU_CGE);

        jouerLaMigration();

        assertThat(droits("CGE", DONNES_AU_CGE)).as("le CGE n'a aucun droit de saisie").isEmpty();
        assertThat(droits("CGEA", DONNES_AU_CGE)).as("le CGEA garde ses quatre droits d'avant")
                .containsExactlyInAnyOrderElementsOf(DEJA_AU_CGEA);
        assertThat(nombreDeDroits("CGE")).isEqualTo(cgeAvant);
        assertThat(nombreDeDroits("CGEA")).isEqualTo(cgeaAvant);
        assertThat(nombreDeDroits("AGENT_BRPD")).as("l'agent BRPD n'est pas touché").isEqualTo(brpdAvant);
    }

    @Test
    void surUneBaseSansLAncienneMigrationElleNeChangeRien() throws Exception {
        int cge = nombreDeDroits("CGE");
        int cgea = nombreDeDroits("CGEA");
        int brpd = nombreDeDroits("AGENT_BRPD");
        int admin = nombreDeDroits("ADMIN_DDIC");

        jouerLaMigration();

        assertThat(nombreDeDroits("CGE")).isEqualTo(cge);
        assertThat(nombreDeDroits("CGEA")).isEqualTo(cgea);
        assertThat(nombreDeDroits("AGENT_BRPD")).isEqualTo(brpd);
        assertThat(nombreDeDroits("ADMIN_DDIC")).isEqualTo(admin);
    }

    @Test
    void lAdministrateurEtLAgentBrpdGardentLeTravailDeSaisie() throws Exception {
        jouerLaMigration();

        assertThat(droits("AGENT_BRPD", List.of("DOSSIER_ENREGISTRER", "DOSSIER_MODIFIER", "PARTIE_VISEE_GERER")))
                .containsExactlyInAnyOrder("DOSSIER_ENREGISTRER", "DOSSIER_MODIFIER", "PARTIE_VISEE_GERER");
        assertThat(droits("ADMIN_DDIC", List.of("DOSSIER_ENREGISTRER", "DOSSIER_MODIFIER")))
                .containsExactlyInAnyOrder("DOSSIER_ENREGISTRER", "DOSSIER_MODIFIER");
    }
}
