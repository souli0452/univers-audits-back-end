package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration 038 : le téléchargement d'une pièce jointe devient un droit à part, et le CGE et le CGEA, au-dessus de
 * l'agent BRPD, peuvent faire son travail (suppléance, ajustable dans l'écran Rôles & Permissions).
 */
@SpringBootTest
class Migration038IntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private Set<String> roles(String permission) {
        return new TreeSet<>(jdbc.queryForList(
                "SELECT r.role_key FROM role_permission rp JOIN role_definition r ON r.id = rp.role_id "
                        + "JOIN permission p ON p.id = rp.permission_id WHERE p.permission_key = ? AND r.active",
                String.class, permission));
    }

    @Test
    void telechargerUnePieceJointeEstReserveAuxRolesDeDirectionEtAuChefDuDei() {
        assertThat(roles("PIECE_JOINTE_TELECHARGER")).containsExactly("ADMIN_DDIC", "CGE", "CGEA", "CHEF_DEI");
    }

    @Test
    void lAgentBrpdEtLesAutresMetiersConsultentSansTelecharger() {
        assertThat(roles("PIECE_JOINTE_VOIR")).contains("AGENT_BRPD", "CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT", "MEMBRE_CTADP");
        assertThat(roles("PIECE_JOINTE_TELECHARGER")).doesNotContain("AGENT_BRPD", "CONSEILLER_JURIDIQUE",
                "CONTROLEUR_ETAT", "MEMBRE_CTADP", "DCP");
    }

    @Test
    void leCgeaEtLeCgePeuventEnregistrerEtCompleterUnDossierEnSuppleanceDeLAgentBrpd() {
        for (String permission : List.of("DOSSIER_ENREGISTRER", "DOSSIER_MODIFIER", "PARTIE_VISEE_GERER",
                "TEMOIN_GERER", "PIECE_JOINTE_RECLASSER", "INFO_PREOCCUPANTE_CREER", "INFO_PREOCCUPANTE_TRAITER")) {
            assertThat(roles(permission)).as(permission).contains("AGENT_BRPD", "CGEA", "CGE");
        }
    }

    @Test
    void laSuppleanceNeDonneAuxSuperieursNiAdministrationNiIdentiteProtegeeDeLAgent() {
        assertThat(roles("AGENT_CREER")).doesNotContain("CGE", "CGEA", "AGENT_BRPD");
        assertThat(roles("AUDIT_JOURNAL_VOIR")).doesNotContain("CGE", "CGEA", "AGENT_BRPD");
    }

    @Test
    void lesRolesSansRapportAvecLEnregistrementNeLeRecoiventPas() {
        assertThat(roles("DOSSIER_ENREGISTRER")).doesNotContain("DCP", "MEMBRE_CTADP", "CONSEILLER_JURIDIQUE",
                "CONTROLEUR_ETAT", "CHEF_DEI", "CHEF_DAC");
    }
}
