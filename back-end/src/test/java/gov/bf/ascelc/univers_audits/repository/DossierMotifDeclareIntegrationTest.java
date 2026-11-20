package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.MotifSignalement;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Avec la vraie base : la migration 040 accepte le motif, et chaque motif renvoie à une allégation qui existe. */
@SpringBootTest
@Transactional
class DossierMotifDeclareIntegrationTest {

    @Autowired private DossierRepository dossiers;
    @Autowired private TypeInfractionRepository infractions;

    @Test
    void leMotifDeclareEstEnregistreEtRelu() {
        Dossier saved = dossiers.saveAndFlush(Dossier.builder().accessCode("MOTIF001").object("Objet de test")
                .type(TypeSaisine.DENONCIATION).submissionMode(SubmissionMode.WEB_FORM)
                .motifDeclare(MotifSignalement.CORRUPTION).build());

        assertThat(dossiers.findById(saved.getId()).orElseThrow().getMotifDeclare())
                .isEqualTo(MotifSignalement.CORRUPTION);
    }

    @Test
    void unDossierSansMotifResteSansMotif() {
        Dossier saved = dossiers.saveAndFlush(Dossier.builder().accessCode("MOTIF002").object("Objet de test")
                .type(TypeSaisine.DENONCIATION).submissionMode(SubmissionMode.WEB_FORM).build());

        assertThat(dossiers.findById(saved.getId()).orElseThrow().getMotifDeclare()).isNull();
    }

    @Test
    void chaqueMotifRenvoieAUneAllegationExistantEtActiveEnBase() {
        for (MotifSignalement motif : MotifSignalement.values()) {
            motif.allegation().ifPresent(code -> assertThat(infractions.findByCode(code))
                    .as("allégation « %s » du motif %s", code, motif)
                    .hasValueSatisfying(t -> assertThat(t.getActif()).isTrue()));
        }
    }
}
