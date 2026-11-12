package gov.bf.ascelc.univers_audits.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessMatrixServiceTest {

    @Test
    void roles_litLesExpressionsHasAnyRoleEtHasRole() {
        assertThat(AccessMatrixService.roles("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')"))
                .containsExactlyInAnyOrder("CGEA", "CGE", "ADMIN_DDIC");
        assertThat(AccessMatrixService.roles("hasRole('ADMIN_DDIC')")).containsExactly("ADMIN_DDIC");
        assertThat(AccessMatrixService.roles("hasRole('CGE') or hasAnyRole('CGEA','DCP')"))
                .containsExactlyInAnyOrder("CGE", "CGEA", "DCP");
    }

    @Test
    void roles_isAuthenticatedSignifieToutUtilisateurConnecte() {
        assertThat(AccessMatrixService.roles("isAuthenticated()")).isNull();
    }

    @Test
    void roles_uneRegleSpecifiqueNEstPasRepresentee() {
        assertThat(AccessMatrixService.roles("@accessGuard.canSee(#id)")).isEmpty();
    }

    @Test
    void calculer_compteLectureEtActionParRole() {
        var endpoints = List.of(
                new AccessMatrixService.Endpoint("Dossier", true, Set.of("CGE", "CGEA")),
                new AccessMatrixService.Endpoint("Dossier", true, null),
                new AccessMatrixService.Endpoint("Dossier", false, Set.of("CGE")));

        var modules = AccessMatrixService.calculer(endpoints, List.of("CGE", "CGEA", "AGENT_BRPD"));

        assertThat(modules).hasSize(1);
        var dossier = modules.get(0);
        assertThat(dossier.libelle()).isEqualTo("Dossiers");
        assertThat(dossier.totalConsulter()).isEqualTo(2);
        assertThat(dossier.totalAgir()).isEqualTo(1);
        assertThat(dossier.parRole().get("CGE")).isEqualTo(new AccessMatrixService.Cellule(2, 1));
        assertThat(dossier.parRole().get("CGEA")).isEqualTo(new AccessMatrixService.Cellule(2, 0));
        // un point d'entrée ouvert à tout utilisateur connecté compte pour tous les rôles
        assertThat(dossier.parRole().get("AGENT_BRPD")).isEqualTo(new AccessMatrixService.Cellule(1, 0));
    }

    @Test
    void libelle_decoupeUnNomInconnuEnMots() {
        assertThat(AccessMatrixService.libelle("MissionSuivi")).isEqualTo("Mission Suivi");
    }
}
