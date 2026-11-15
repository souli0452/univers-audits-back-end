package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Lit les attributions réellement semées en base par la migration 028. */
@SpringBootTest
class PermissionResolverIntegrationTest {

    @Autowired
    private PermissionResolver resolver;

    @Test
    void leCgeRecoitSesPermissionsMetierMaisPasCellesDuCgea() {
        Set<String> cge = resolver.resolve(List.of("CGE"));

        assertThat(cge).contains(
                AppPermission.DOSSIER_VOIR.authority(),
                AppPermission.DOSSIER_CONFIDENTIALITE_MODIFIER.authority(),
                AppPermission.INVESTIGATION_CGE_STATUER.authority());
        assertThat(cge).doesNotContain(
                AppPermission.INVESTIGATION_OUVRIR.authority(),
                AppPermission.SEANCE_CTADP_TENIR.authority());
    }

    @Test
    void laDcpNeRecoitQueLesStatistiquesGlobales() {
        assertThat(resolver.resolve(List.of("DCP"))).containsExactlyInAnyOrder(
                AppPermission.STATISTIQUE_GLOBALE_VOIR.authority(),
                AppPermission.STATISTIQUE_EXPORTER.authority());
    }

    @Test
    void lAdministrateurRecoitToutesLesPermissionsProtegees() {
        Set<String> admin = resolver.resolve(List.of("ADMIN_DDIC"));

        for (AppPermission p : AppPermission.values()) {
            if (p.protegee()) {
                assertThat(admin).as(p.key()).contains(p.authority());
            }
        }
    }

    @Test
    void lesRolesTechniquesDeKeycloakNeDonnentAucuneAutorite() {
        assertThat(resolver.resolve(List.of("default-roles-asce-lc", "offline_access", "uma_authorization"))).isEmpty();
    }

    @Test
    void unUtilisateurACumulantDeuxRolesRecoitLUnion() {
        Set<String> union = resolver.resolve(List.of("CGE", "DCP"));

        assertThat(union).contains(
                AppPermission.DOSSIER_VOIR.authority(), AppPermission.STATISTIQUE_EXPORTER.authority());
    }

    @Test
    void lesPermissionsVerifieesDansLeCodeSontAttribueesCommeAvantLaBascule() {
        // Avant : canSeeConfidential() = CGE, CGEA ou ADMIN_DDIC ; protection des lanceurs d'alerte = CGE ou CGEA.
        // Depuis le 2026-11-15 l'administrateur voit aussi l'identité des lanceurs d'alerte protégés.
        for (String role : List.of("ADMIN_DDIC", "CGE", "CGEA", "CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT",
                "MEMBRE_CTADP", "AGENT_BRPD", "DCP")) {
            Set<String> autorites = resolver.resolve(List.of(role));
            assertThat(autorites.contains(AppPermission.DOSSIER_VOIR_TOUS.authority()))
                    .as(role + " : voir tous les dossiers").isEqualTo(Set.of("ADMIN_DDIC", "CGE", "CGEA").contains(role));
            assertThat(autorites.contains(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER.authority()))
                    .as(role + " : traiter les lanceurs d'alerte").isEqualTo(Set.of("ADMIN_DDIC", "CGE", "CGEA").contains(role));
        }
    }

    @Test
    void lesAnciennesPermissionsIndicativesNeSontJamaisAccordees() {
        assertThat(resolver.resolve(List.of("ADMIN_DDIC", "CGE", "CGEA", "AGENT_BRPD")))
                .noneMatch(a -> a.equals("PERM_VOIR_DOSSIER") || a.equals("PERM_GERER_ROLES"));
    }
}
