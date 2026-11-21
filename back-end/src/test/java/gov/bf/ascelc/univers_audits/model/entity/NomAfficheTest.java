package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Le nom affiché d'un déclarant ou d'un témoin est en français : il apparaît à l'écran et dans les documents. */
class NomAfficheTest {

    @Test
    void declarantSansNomNiOrganisation_estAfficheNonRenseigne() {
        Declarant declarant = Declarant.builder().build();

        assertThat(declarant.getDisplayName()).isEqualTo("Non renseigné");
    }

    @Test
    void declarantAnonyme_estAfficheAnonyme() {
        Declarant declarant = Declarant.builder().typeDeclarant(TypeDeclarant.ANONYMOUS).build();

        assertThat(declarant.getDisplayName()).isEqualTo("Anonyme");
    }

    @Test
    void autoSaisineDeLASCE_estAfficheEnFrancais() {
        Declarant declarant = Declarant.builder().typeDeclarant(TypeDeclarant.ASCE_SELF_REFERRAL).build();

        assertThat(declarant.getDisplayName()).isEqualTo("ASCE-LC (auto-saisine)");
    }

    @Test
    void declarantAvecOrganisation_afficheLOrganisation() {
        Declarant declarant = Declarant.builder().organizationName("Société Test").build();

        assertThat(declarant.getDisplayName()).isEqualTo("Société Test");
    }

    @Test
    void declarantAvecNomEtPrenom_afficheLePrenomPuisLeNom() {
        Declarant declarant = Declarant.builder().firstName("Awa").lastName("Ouédraogo").build();

        assertThat(declarant.getDisplayName()).isEqualTo("Awa Ouédraogo");
    }

    @Test
    void temoinSansNom_estAfficheNonRenseigne() {
        Witness temoin = Witness.builder().build();

        assertThat(temoin.getDisplayName()).isEqualTo("Non renseigné");
    }

    @Test
    void temoinAnonyme_resteTemoinAnonyme() {
        Witness temoin = Witness.builder().anonymous(true).build();

        assertThat(temoin.getDisplayName()).isEqualTo("Témoin anonyme");
    }
}
