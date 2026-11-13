package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleCombinationPolicyTest {

    @Test
    void unSeulRole_estToujoursAccepte() {
        assertThatCode(() -> RoleCombinationPolicy.verifier(List.of("CGE"))).doesNotThrowAnyException();
    }

    @Test
    void listeAbsenteOuVide_estAcceptee() {
        assertThatCode(() -> RoleCombinationPolicy.verifier(null)).doesNotThrowAnyException();
        assertThatCode(() -> RoleCombinationPolicy.verifier(List.of())).doesNotThrowAnyException();
    }

    @Test
    void brpdEtCge_estInterdit() {
        assertThatThrownBy(() -> RoleCombinationPolicy.verifier(List.of("AGENT_BRPD", "CGE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cumul de rôles interdit")
                .hasMessageContaining("AGENT_BRPD + CGE");
    }

    @Test
    void ordreDesRoles_neChangeRien() {
        assertThatThrownBy(() -> RoleCombinationPolicy.verifier(List.of("CGE", "AGENT_BRPD")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cgeEtCgea_estInterdit() {
        assertThat(RoleCombinationPolicy.interdits(List.of("CGE", "CGEA"))).hasSize(1);
    }

    @Test
    void controleurEtatAvecJuridique_estInterdit() {
        assertThat(RoleCombinationPolicy.interdits(List.of("CONTROLEUR_ETAT", "CONSEILLER_JURIDIQUE"))).hasSize(1);
    }

    @Test
    void dcpAvecUnRoleDeDossier_estInterdit() {
        for (String metier : List.of("AGENT_BRPD", "CONSEILLER_JURIDIQUE", "MEMBRE_CTADP", "CGEA", "CGE", "CONTROLEUR_ETAT")) {
            assertThat(RoleCombinationPolicy.interdits(List.of("DCP", metier)))
                    .as("DCP + " + metier).hasSize(1);
        }
    }

    @Test
    void cumulASurveiller_neBloquePas() {
        assertThatCode(() -> RoleCombinationPolicy.verifier(List.of("MEMBRE_CTADP", "CGEA")))
                .doesNotThrowAnyException();
        assertThat(RoleCombinationPolicy.interdits(List.of("MEMBRE_CTADP", "CGEA"))).isEmpty();
    }

    @Test
    void adminDdic_neBloquePasLesCumuls() {
        assertThatCode(() -> RoleCombinationPolicy.verifier(List.of(
                "ADMIN_DDIC", "AGENT_BRPD", "CGE", "CGEA", "CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT", "MEMBRE_CTADP")))
                .doesNotThrowAnyException();
        assertThatCode(() -> RoleCombinationPolicy.verifier(List.of("ADMIN_DDIC", "DCP")))
                .doesNotThrowAnyException();
    }

    @Test
    void rolesSansConflit_sontAcceptes() {
        assertThatCode(() -> RoleCombinationPolicy.verifier(List.of("CGEA", "CONSEILLER_JURIDIQUE", "MEMBRE_CTADP")))
                .doesNotThrowAnyException();
    }

    @Test
    void chaqueRegleReferenceDeuxRolesDifferents() {
        RoleCombinationPolicy.regles().forEach(r -> {
            assertThat(r.roleA()).isNotEqualTo(r.roleB());
            assertThat(r.raison()).isNotBlank();
        });
    }
}
