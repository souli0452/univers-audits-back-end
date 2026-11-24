package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.MotifSignalement;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** « Autre » ouvre un champ libre : obligatoire, 150 mots au maximum. */
class DossierCreateRequestMotifTest {

    private final Validator validateur = Validation.buildDefaultValidatorFactory().getValidator();

    private DossierCreateRequest requete(MotifSignalement motif, String autre) {
        return DossierCreateRequest.builder().submissionMode(SubmissionMode.WEB_FORM).object("Objet")
                .motifDeclare(motif).motifAutre(autre).build();
    }

    private String mots(int n) {
        return String.join(" ", Collections.nCopies(n, "mot"));
    }

    @Test
    void autreSansPrecision_estRefuse() {
        assertThat(validateur.validate(requete(MotifSignalement.AUTRE, null))).hasSize(1);
        assertThat(validateur.validate(requete(MotifSignalement.AUTRE, "   "))).hasSize(1);
    }

    @Test
    void autreAvecUnePrecision_estAccepte() {
        assertThat(validateur.validate(requete(MotifSignalement.AUTRE, "Un chef de service vend des bourses"))).isEmpty();
    }

    @Test
    void centCinquanteMotsPassent_centCinquanteEtUnNon() {
        assertThat(validateur.validate(requete(MotifSignalement.AUTRE, mots(150)))).isEmpty();
        assertThat(validateur.validate(requete(MotifSignalement.AUTRE, mots(151)))).hasSize(1);
    }

    @Test
    void lesEspacesMultiplesEtLesRetoursALaLigneNeGonflentPasLeCompte() {
        assertThat(validateur.validate(requete(MotifSignalement.AUTRE, mots(150).replace(" ", "  \n  ")))).isEmpty();
    }

    @Test
    void uneAllegationPrecise_nExigeRien() {
        assertThat(validateur.validate(requete(MotifSignalement.CORRUPTION, null))).isEmpty();
        assertThat(validateur.validate(requete(null, null))).isEmpty();
    }

    @Test
    void unTexteLibreAvecUneAllegationPrecise_n_estPasUneErreur_ilSeraIgnore() {
        assertThat(validateur.validate(requete(MotifSignalement.FRAUDE, "texte oublié"))).isEmpty();
    }
}
