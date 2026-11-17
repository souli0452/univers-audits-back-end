package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.enums.TypologieInstitutionnelle;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Avec la vraie base : la migration 035 accepte les nouvelles catégories, Messenger, la région et la typologie. */
@SpringBootTest
@Transactional
class DossierSaisineCategoriesIntegrationTest {

    @Autowired
    private DossierRepository dossiers;

    private Dossier dossier(String code, TypeSaisine type) {
        return Dossier.builder().accessCode(code).object("Objet de test").type(type).submissionMode(SubmissionMode.SOCIAL_MEDIA)
                .socialPlatform(SocialPlatform.MESSENGER).regionOrigine(RegionOrigine.DIASPORA)
                .typologieInstitutionnelle(TypologieInstitutionnelle.FONDS_FINANCEMENT).build();
    }

    @Test
    void lesNouvellesCategoriesMessengerRegionEtTypologieSontEnregistrees() {
        for (TypeSaisine type : new TypeSaisine[]{TypeSaisine.PLAINTE_DENONCIATION, TypeSaisine.DEMANDE_INTERVENTION}) {
            Dossier saved = dossiers.saveAndFlush(dossier("T" + type.name().substring(0, 8), type));

            Dossier relu = dossiers.findById(saved.getId()).orElseThrow();
            assertThat(relu.getType()).isEqualTo(type);
            assertThat(relu.getSocialPlatform()).isEqualTo(SocialPlatform.MESSENGER);
            assertThat(relu.getRegionOrigine()).isEqualTo(RegionOrigine.DIASPORA);
            assertThat(relu.getTypologieInstitutionnelle()).isEqualTo(TypologieInstitutionnelle.FONDS_FINANCEMENT);
        }
    }
}
