package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Décision du 2026-11-14 : ADMIN_DDIC garde tout (technique et métier) ; CGE et CGEA ne font que le métier,
 * aucune administration. Lit les attributions réellement présentes en base après les migrations.
 */
@SpringBootTest
class CgeCgeaMetierSeulementIntegrationTest {

    private static final Set<String> CATEGORIES_TECHNIQUES = Set.of("Administration", "Paramétrage", "Audit");

    @Autowired
    private PermissionResolver resolver;

    private static boolean technique(AppPermission p) {
        return CATEGORIES_TECHNIQUES.contains(p.category())
                || p == AppPermission.NOTIFICATION_FILE_VOIR
                || p == AppPermission.NOTIFICATION_FILE_GERER;
    }

    @Test
    void cgeEtCgeaNeDetiennentAucunePermissionDAdministration() {
        for (String role : List.of("CGE", "CGEA")) {
            Set<String> autorites = resolver.resolve(List.of(role));
            for (AppPermission p : AppPermission.values()) {
                if (technique(p)) {
                    assertThat(autorites).as(role + " ne doit pas avoir " + p.key()).doesNotContain(p.authority());
                }
            }
        }
    }

    @Test
    void lAdministrateurGardeToutLeTechniqueEtLeMetier() {
        Set<String> admin = resolver.resolve(List.of("ADMIN_DDIC"));
        for (AppPermission p : AppPermission.values()) {
            // Seule exception : sa liste personnelle de dossiers (il n'en a pas). Décision du 2026-11-15 :
            // l'administrateur voit tout, y compris l'identité des lanceurs d'alerte protégés.
            boolean exception = p == AppPermission.DOSSIER_MES_DOSSIERS_VOIR;
            if (!exception) {
                assertThat(admin).as("ADMIN_DDIC doit avoir " + p.key()).contains(p.authority());
            }
        }
    }

    @Test
    void lAdministrateurVoitLIdentiteDesLanceursDAlerteProteges() {
        assertThat(resolver.resolve(List.of("ADMIN_DDIC")))
                .contains(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER.authority());
    }

    @Test
    void leRegistreDesAuditionsEstOuvertAuCgeAuCgeaEtALAdministrateurSeulement() {
        // Manuel des procédures, processus C (p. 54-56) : l'accès aux dossiers d'investigation est limité au CGE,
        // au CGEA et au chef du DEI ; décision du 2026-11-16 : le CGE rejoint le CGEA et l'administrateur.
        for (String role : List.of("ADMIN_DDIC", "CGE", "CGEA")) {
            assertThat(resolver.resolve(List.of(role))).as(role)
                    .contains(AppPermission.REGISTRE_AUDITIONS_VOIR.authority());
        }
        for (String role : List.of("CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT", "MEMBRE_CTADP", "AGENT_BRPD", "DCP")) {
            assertThat(resolver.resolve(List.of(role))).as(role)
                    .doesNotContain(AppPermission.REGISTRE_AUDITIONS_VOIR.authority());
        }
    }

    @Test
    void leCgeGardeSesPouvoirsMetier() {
        assertThat(resolver.resolve(List.of("CGE"))).contains(
                AppPermission.DOSSIER_VOIR_TOUS.authority(),
                AppPermission.DOSSIER_CONFIDENTIALITE_MODIFIER.authority(),
                AppPermission.HABILITATION_ACCORDER.authority(),
                AppPermission.INVESTIGATION_CGE_STATUER.authority(),
                AppPermission.STATISTIQUE_DEPASSEMENTS_VOIR.authority());
    }

    @Test
    void leCgeaGardeSesPouvoirsMetier() {
        assertThat(resolver.resolve(List.of("CGEA"))).contains(
                AppPermission.DOSSIER_VOIR_TOUS.authority(),
                AppPermission.REGISTRE_AUDITIONS_VOIR.authority(),
                AppPermission.INVESTIGATION_OUVRIR.authority(),
                AppPermission.SEANCE_CTADP_TENIR.authority(),
                AppPermission.FICHE_AFFECTATION_AFFECTER.authority());
    }

    @Test
    void aucunAutreRoleMetierNADePermissionTechnique() {
        for (String role : List.of("CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT", "MEMBRE_CTADP", "AGENT_BRPD", "DCP")) {
            Set<String> autorites = resolver.resolve(List.of(role));
            for (AppPermission p : AppPermission.values()) {
                if (technique(p)) {
                    assertThat(autorites).as(role + " ne doit pas avoir " + p.key()).doesNotContain(p.authority());
                }
            }
        }
    }

    @Test
    void lAnnuaireDesAgentsEstOuvertAuxRolesMetierMaisPasALaDcp() {
        for (String role : List.of("ADMIN_DDIC", "CGE", "CGEA", "CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT",
                "MEMBRE_CTADP", "AGENT_BRPD")) {
            assertThat(resolver.resolve(List.of(role))).as(role).contains(AppPermission.AGENT_ANNUAIRE_VOIR.authority());
        }
        assertThat(resolver.resolve(List.of("DCP"))).doesNotContain(AppPermission.AGENT_ANNUAIRE_VOIR.authority());
    }
}
