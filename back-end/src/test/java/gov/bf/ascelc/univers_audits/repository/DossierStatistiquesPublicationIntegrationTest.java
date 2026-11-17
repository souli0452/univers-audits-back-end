package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.enums.TypologieInstitutionnelle;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.service.PublicationTrimestrielleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avec la vraie base : la situation trimestrielle compte bien des dossiers réels. Année 2099, pour ne jamais se
 * mélanger aux données de la base ; tout est annulé à la fin du test.
 */
@SpringBootTest
@Transactional
class DossierStatistiquesPublicationIntegrationTest {

    private static final Instant T1_2099 = Instant.parse("2099-02-10T10:00:00Z");

    @Autowired private DossierRepository dossiers;
    @Autowired private EtudeOpportuniteRepository etudes;
    @Autowired private TypeInfractionRepository infractions;
    @Autowired private PublicationTrimestrielleService service;
    @Autowired private JdbcTemplate jdbc;

    private int compteur;

    private Dossier dossier(TypeSaisine type, SubmissionMode mode, SocialPlatform plateforme, RegionOrigine region,
                            TypologieInstitutionnelle typologie, DossierStatus statut) {
        return dossiers.saveAndFlush(Dossier.builder()
                .accessCode("S" + (++compteur) + "-" + (System.nanoTime() % 100000)).object("Objet de test")
                .type(type).submissionMode(mode).socialPlatform(plateforme).regionOrigine(region)
                .typologieInstitutionnelle(typologie).status(statut).receptionDate(T1_2099).build());
    }

    private void etude(Dossier d, TypeInfraction infraction, Boolean competence, Boolean preuves) {
        etudes.saveAndFlush(EtudeOpportunite.builder().dossier(d).typeInfraction(infraction)
                .competenceAsceLc(competence).preuvesSuffisantes(preuves).build());
    }

    @Test
    void lesHuitTableauxCompterntLesVraisDossiersDuTrimestre() {
        TypeInfraction corruption = infractions.findAll().get(0);

        Dossier a = dossier(TypeSaisine.DENONCIATION, SubmissionMode.SOCIAL_MEDIA, SocialPlatform.MESSENGER,
                RegionOrigine.KADIOGO, TypologieInstitutionnelle.SOCIETES_D_ETAT, DossierStatus.EN_INVESTIGATION);
        etude(a, corruption, true, true);
        Dossier b = dossier(TypeSaisine.DEMANDE_INTERVENTION, SubmissionMode.SOCIAL_MEDIA, SocialPlatform.WHATSAPP,
                RegionOrigine.DIASPORA, null, DossierStatus.CLASSE);
        etude(b, null, false, null);
        Dossier c = dossier(TypeSaisine.PLAINTE_DENONCIATION, SubmissionMode.PAPER_FORM, null,
                null, TypologieInstitutionnelle.SOCIETES_D_ETAT, DossierStatus.IRRECEVABLE);
        etude(c, null, true, false);
        dossier(TypeSaisine.PLAINTE, SubmissionMode.WEB_FORM, null, RegionOrigine.KADIOGO, null, DossierStatus.SOUMIS);
        // hors trimestre : ne doit pas être compté
        dossiers.saveAndFlush(Dossier.builder().accessCode("HORS-" + (System.nanoTime() % 100000))
                .object("Hors période").type(TypeSaisine.PLAINTE).submissionMode(SubmissionMode.WEB_FORM)
                .status(DossierStatus.SOUMIS).receptionDate(Instant.parse("2099-08-01T10:00:00Z")).build());

        PublicationTrimestrielleResponse t1 = service.situation(2099, 1);

        assertThat(t1.total()).isEqualTo(4);
        assertThat(parCode(t1.categories(), "PLAINTE_DENONCIATION")).isEqualTo(1);
        assertThat(parCode(t1.categories(), "DEMANDE_INTERVENTION")).isEqualTo(1);
        assertThat(parCode(t1.modesDeSaisine(), "MESSENGER")).isEqualTo(1);
        assertThat(parCode(t1.modesDeSaisine(), "WHATSAPP")).isEqualTo(1);
        assertThat(parCode(t1.modesDeSaisine(), "ECRIT")).isEqualTo(1);
        assertThat(parCode(t1.modesDeSaisine(), "PORTAIL")).isEqualTo(1);
        assertThat(parCode(t1.typologiesInstitutionnelles(), "SOCIETES_D_ETAT")).isEqualTo(2);
        assertThat(parCode(t1.typologiesInstitutionnelles(), "NON_PRECISE")).isEqualTo(2);
        assertThat(parCode(t1.originesGeographiques(), "KADIOGO")).isEqualTo(2);
        assertThat(parCode(t1.originesGeographiques(), "DIASPORA")).isEqualTo(1);
        assertThat(parCode(t1.originesGeographiques(), "NON_PRECISE")).isEqualTo(1);
        assertThat(parCode(t1.allegations(), corruption.getLibelle())).isEqualTo(1);
        assertThat(parCode(t1.allegations(), "NON_QUALIFIE")).isEqualTo(3);
        assertThat(parCode(t1.traitement(), "EN_COURS")).isEqualTo(1);
        assertThat(parCode(t1.traitement(), "BOUCLES")).isEqualTo(1);
        assertThat(parCode(t1.traitement(), "STOCK")).isEqualTo(2);
        assertThat(parCode(t1.recevabilite(), "CLASSES_INCOMPETENCE")).isEqualTo(1);
        assertThat(parCode(t1.recevabilite(), "CLASSES_PREUVES_INSUFFISANTES")).isEqualTo(1);
        assertThat(parCode(t1.recevabilite(), "RECEVABLES")).isEqualTo(2);
        assertThat(service.situation(2099, 3).total()).isEqualTo(1);
        assertThat(service.situation(2099, 2).total()).isZero();
    }

    private static long parCode(List<PublicationTrimestrielleResponse.Ligne> lignes, String code) {
        return lignes.stream().filter(l -> l.code().equals(code)).findFirst().orElseThrow().nombre();
    }
}
