package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.enums.TypologieInstitutionnelle;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse.Ligne;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/** « Situation des plaintes et dénonciations » : les huit tableaux publiés chaque trimestre sur le site. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicationTrimestrielleServiceTest {

    @Mock private DossierRepository dossiers;
    @Mock private InvestigationRepository investigations;

    private PublicationTrimestrielleService service;

    @BeforeEach
    void setUp() {
        service = new PublicationTrimestrielleService(dossiers, investigations);
        when(dossiers.countEnregistresBetween(any(), any())).thenReturn(74L);
        when(dossiers.countByTypeEnregistresBetween(any(), any())).thenReturn(lignes(
                new Object[]{TypeSaisine.DENONCIATION, 54L}, new Object[]{TypeSaisine.PLAINTE, 12L},
                new Object[]{TypeSaisine.PLAINTE_DENONCIATION, 2L}, new Object[]{TypeSaisine.DEMANDE_INTERVENTION, 6L}));
        when(dossiers.countByModeEtPlateformeBetween(any(), any())).thenReturn(lignes(
                new Object[]{SubmissionMode.IN_PERSON, null, 11L}, new Object[]{SubmissionMode.AUDIO_COUNTER, null, 2L},
                new Object[]{SubmissionMode.PAPER_FORM, null, 20L}, new Object[]{SubmissionMode.POSTAL_MAIL, null, 9L},
                new Object[]{SubmissionMode.EMAIL, null, 6L}, new Object[]{SubmissionMode.PHONE, null, 2L},
                new Object[]{SubmissionMode.SOCIAL_MEDIA, SocialPlatform.MESSENGER, 1L},
                new Object[]{SubmissionMode.SOCIAL_MEDIA, SocialPlatform.WHATSAPP, 23L}));
        when(dossiers.countByTypologieBetween(any(), any())).thenReturn(lignes(
                new Object[]{TypologieInstitutionnelle.MINISTERES_ADMINISTRATIONS_CENTRALES, 37L},
                new Object[]{TypologieInstitutionnelle.SOCIETES_D_ETAT, 15L}, new Object[]{null, 22L}));
        when(dossiers.countByRegionOrigineBetween(any(), any())).thenReturn(lignes(
                new Object[]{RegionOrigine.KADIOGO, 48L}, new Object[]{RegionOrigine.GUIRIKO, 7L},
                new Object[]{RegionOrigine.AUTRES_REGIONS, 18L}, new Object[]{RegionOrigine.DIASPORA, 1L}));
        when(dossiers.countByAllegationBetween(any(), any())).thenReturn(lignes(
                new Object[]{"Corruption", 12L}, new Object[]{"Détournement et malversations financières", 17L},
                new Object[]{null, 45L}));
        when(dossiers.countByStatusEnregistresBetween(any(), any())).thenReturn(lignes(
                new Object[]{DossierStatus.EN_INVESTIGATION, 18L}, new Object[]{DossierStatus.CLOS, 5L},
                new Object[]{DossierStatus.RECEVABLE, 40L}, new Object[]{DossierStatus.SOUMIS, 11L}));
        when(dossiers.sumEstimatedLossEnregistresBetween(any(), any())).thenReturn(new BigDecimal("7000000000"));
        when(dossiers.countClassesPourIncompetenceBetween(any(), any(), anyCollection())).thenReturn(0L);
        when(dossiers.countClassesPourPreuvesInsuffisantesBetween(any(), any(), anyCollection())).thenReturn(0L);
        when(investigations.avgDurationInDays(any(), any())).thenReturn(45.0);
        when(dossiers.avgOpportunityStudyDelayInDays(any(), any())).thenReturn(2.0);
    }

    private static List<Object[]> lignes(Object[]... l) {
        return new ArrayList<>(List.of(l));
    }

    private PublicationTrimestrielleResponse t1() {
        return service.situation(2026, "T1");
    }

    private static Ligne ligne(Collection<Ligne> lignes, String code) {
        return lignes.stream().filter(l -> l.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("ligne absente : " + code + " parmi "
                        + lignes.stream().map(Ligne::code).collect(Collectors.toList())));
    }

    private static long somme(Collection<Ligne> lignes) {
        return lignes.stream().mapToLong(Ligne::nombre).sum();
    }

    @Test
    void lePeriodeEtLeTotalSontCeuxDuTrimestre() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(r.annee()).isEqualTo(2026);
        assertThat(r.codePeriode()).isEqualTo("T1");
        assertThat(r.periode()).isEqualTo("T1 2026");
        assertThat(r.total()).isEqualTo(74);
    }

    @Test
    void lesCategoriesReprennentLesQuatreCategoriesDuDocumentEtLeurTaux() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.categories(), "DENONCIATION").nombre()).isEqualTo(54);
        assertThat(ligne(r.categories(), "PLAINTE_DENONCIATION").libelle()).isEqualTo("Plainte-dénonciation");
        assertThat(ligne(r.categories(), "PLAINTE_DENONCIATION").nombre()).isEqualTo(2);
        assertThat(ligne(r.categories(), "DEMANDE_INTERVENTION").nombre()).isEqualTo(6);
        assertThat(ligne(r.categories(), "DENONCIATION").taux()).isEqualTo(72.97);
        assertThat(somme(r.categories())).isEqualTo(74);
        // les catégories vides ne s'affichent pas
        assertThat(r.categories()).extracting(Ligne::code).doesNotContain("SIGNALEMENT", "AUTO_SAISINE");
    }

    @Test
    void lesModesDeSaisineSontRegroupesCommeDansLeDocumentEtDistinguentWhatsAppDeMessenger() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.modesDeSaisine(), "EN_PERSONNE").nombre()).isEqualTo(13);      // en personne + guichet audio
        assertThat(ligne(r.modesDeSaisine(), "ECRIT").nombre()).isEqualTo(29);            // formulaire papier + courrier
        assertThat(ligne(r.modesDeSaisine(), "MAIL").nombre()).isEqualTo(6);
        assertThat(ligne(r.modesDeSaisine(), "TELEPHONE").nombre()).isEqualTo(2);
        assertThat(ligne(r.modesDeSaisine(), "MESSENGER").nombre()).isEqualTo(1);
        assertThat(ligne(r.modesDeSaisine(), "WHATSAPP").nombre()).isEqualTo(23);
        assertThat(somme(r.modesDeSaisine())).isEqualTo(74);
    }

    @Test
    void unReseauSocialAutreQueWhatsAppEtMessengerEtLePortailSontComptesAPart() {
        when(dossiers.countByModeEtPlateformeBetween(any(), any())).thenReturn(lignes(
                new Object[]{SubmissionMode.WEB_FORM, null, 3L},
                new Object[]{SubmissionMode.SOCIAL_MEDIA, SocialPlatform.FACEBOOK, 2L},
                new Object[]{SubmissionMode.SOCIAL_MEDIA, null, 1L},
                new Object[]{SubmissionMode.PRESS_MEDIA, null, 1L}));

        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.modesDeSaisine(), "PORTAIL").nombre()).isEqualTo(3);
        assertThat(ligne(r.modesDeSaisine(), "AUTRES").nombre()).isEqualTo(4);
    }

    @Test
    void laTypologieInstitutionnelleCompteLesDossiersNonRenseignesAPart() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.typologiesInstitutionnelles(), "MINISTERES_ADMINISTRATIONS_CENTRALES").nombre()).isEqualTo(37);
        assertThat(ligne(r.typologiesInstitutionnelles(), "MINISTERES_ADMINISTRATIONS_CENTRALES").taux()).isEqualTo(50.0);
        assertThat(ligne(r.typologiesInstitutionnelles(), "NON_PRECISE").nombre()).isEqualTo(22);
        assertThat(somme(r.typologiesInstitutionnelles())).isEqualTo(74);
    }

    @Test
    void lesAllegationsSontTrieesParNombreEtLesDossiersNonQualifiesSontEnDernier() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(r.allegations()).extracting(Ligne::libelle)
                .containsExactly("Détournement et malversations financières", "Corruption", "Non qualifié");
        assertThat(somme(r.allegations())).isEqualTo(74);
    }

    @Test
    void laSituationDeTraitementPartitionneLesDossiersEnCoursBouclesEtStock() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.traitement(), "ENREGISTRES").nombre()).isEqualTo(74);
        assertThat(ligne(r.traitement(), "EN_COURS").nombre()).isEqualTo(18);
        assertThat(ligne(r.traitement(), "BOUCLES").nombre()).isEqualTo(5);
        assertThat(ligne(r.traitement(), "STOCK").nombre()).isEqualTo(51);
        assertThat(r.enjeuxFinanciers()).isEqualByComparingTo("7000000000");
    }

    @Test
    void laRecevabiliteCompteLesDossiersClassesSansSuiteSelonLeMotif() {
        when(dossiers.countByStatusEnregistresBetween(any(), any())).thenReturn(lignes(
                new Object[]{DossierStatus.EN_INVESTIGATION, 60L}, new Object[]{DossierStatus.IRRECEVABLE, 6L},
                new Object[]{DossierStatus.TRANSFERE, 2L}, new Object[]{DossierStatus.CLASSE, 6L}));
        when(dossiers.countClassesPourIncompetenceBetween(any(), any(), anyCollection())).thenReturn(5L);
        when(dossiers.countClassesPourPreuvesInsuffisantesBetween(any(), any(), anyCollection())).thenReturn(4L);

        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.recevabilite(), "RECEVABLES").nombre()).isEqualTo(60);
        assertThat(ligne(r.recevabilite(), "CLASSES_INCOMPETENCE").nombre()).isEqualTo(5);
        assertThat(ligne(r.recevabilite(), "CLASSES_PREUVES_INSUFFISANTES").nombre()).isEqualTo(4);
        assertThat(ligne(r.recevabilite(), "CLASSES_MOTIF_NON_RENSEIGNE").nombre()).isEqualTo(5);
        assertThat(somme(r.recevabilite())).isEqualTo(74);
    }

    @Test
    void lOrigineGeographiqueCompteLesQuatreZonesEtLesDossiersNonRenseignes() {
        when(dossiers.countByRegionOrigineBetween(any(), any())).thenReturn(lignes(
                new Object[]{RegionOrigine.KADIOGO, 48L}, new Object[]{null, 26L}));

        PublicationTrimestrielleResponse r = t1();

        assertThat(ligne(r.originesGeographiques(), "KADIOGO").nombre()).isEqualTo(48);
        assertThat(ligne(r.originesGeographiques(), "KADIOGO").taux()).isEqualTo(64.86);
        assertThat(ligne(r.originesGeographiques(), "NON_PRECISE").nombre()).isEqualTo(26);
        assertThat(r.originesGeographiques()).extracting(Ligne::code)
                .contains("KADIOGO", "GUIRIKO", "AUTRES_REGIONS", "DIASPORA", "NON_PRECISE");
    }

    @Test
    void laVelocitePrendLeDelaiMoyenDInvestigationCalcule() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(r.velocite()).extracting(PublicationTrimestrielleResponse.Phase::code)
                .containsExactly("ENREGISTRES", "QUALIFIES", "INVESTIGATION", "CLOTURES", "COUR_DES_COMPTES", "STOCK");
        assertThat(phase(r, "INVESTIGATION").delaiMoyenJours()).isEqualTo(45.0);
        assertThat(phase(r, "INVESTIGATION").nombre()).isEqualTo(18);
        assertThat(phase(r, "CLOTURES").delaiMoyenJours()).isEqualTo(45.0);
        assertThat(phase(r, "CLOTURES").nombre()).isEqualTo(5);
    }

    @Test
    void laVelociteDonneLeDelaiDeQualificationEtLeStockEnInstance() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(phase(r, "QUALIFIES").nombre()).isEqualTo(63);               // 74 − 11 dossiers pas encore étudiés
        assertThat(phase(r, "QUALIFIES").delaiMoyenJours()).isEqualTo(2.0);
        assertThat(phase(r, "STOCK").nombre()).isEqualTo(51);
        assertThat(phase(r, "STOCK").delaiMoyenJours()).isNull();
    }

    @Test
    void laTransmissionALaCourDesComptesNEstPasSuivieEtLeDitHonnetement() {
        PublicationTrimestrielleResponse r = t1();

        assertThat(phase(r, "COUR_DES_COMPTES").nombre()).isZero();
        assertThat(phase(r, "COUR_DES_COMPTES").delaiMoyenJours()).isNull();
    }

    private static PublicationTrimestrielleResponse.Phase phase(PublicationTrimestrielleResponse r, String code) {
        return r.velocite().stream().filter(p -> p.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void unTrimestreSansDossierDonneDesTableauxVidesSansDivisionParZero() {
        when(dossiers.countEnregistresBetween(any(), any())).thenReturn(0L);
        when(dossiers.countByTypeEnregistresBetween(any(), any())).thenReturn(List.of());
        when(dossiers.countByModeEtPlateformeBetween(any(), any())).thenReturn(List.of());
        when(dossiers.countByTypologieBetween(any(), any())).thenReturn(List.of());
        when(dossiers.countByRegionOrigineBetween(any(), any())).thenReturn(List.of());
        when(dossiers.countByAllegationBetween(any(), any())).thenReturn(List.of());
        when(dossiers.countByStatusEnregistresBetween(any(), any())).thenReturn(List.of());
        when(dossiers.sumEstimatedLossEnregistresBetween(any(), any())).thenReturn(null);
        when(investigations.avgDurationInDays(any(), any())).thenReturn(null);

        PublicationTrimestrielleResponse r = t1();

        assertThat(r.total()).isZero();
        assertThat(r.categories()).allSatisfy(l -> assertThat(l.taux()).isZero());
        assertThat(r.enjeuxFinanciers()).isEqualByComparingTo("0");
        assertThat(ligne(r.traitement(), "STOCK").nombre()).isZero();
    }

    @Test
    void unePeriodeInconnueEstRefusee() {
        assertThatThrownBy(() -> service.situation(2026, "T5")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.situation(2026, "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.situation(2026, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void leSemestreEtLAnneeCouvrentLesBonsMois() {
        org.mockito.ArgumentCaptor<Instant> debut = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<Instant> fin = org.mockito.ArgumentCaptor.forClass(Instant.class);

        PublicationTrimestrielleResponse s2 = service.situation(2026, "s2");
        org.mockito.Mockito.verify(dossiers).countEnregistresBetween(debut.capture(), fin.capture());
        assertThat(s2.periode()).isEqualTo("S2 2026");
        assertThat(debut.getValue()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(fin.getValue()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));

        org.mockito.Mockito.clearInvocations(dossiers);
        PublicationTrimestrielleResponse annee = service.situation(2026, "ANNEE");
        org.mockito.Mockito.verify(dossiers).countEnregistresBetween(debut.capture(), fin.capture());
        assertThat(annee.periode()).isEqualTo("Année 2026");
        assertThat(debut.getValue()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(fin.getValue()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void laSituationLitLesBornesDuTrimestreDemande() {
        service.situation(2026, "T2");

        org.mockito.ArgumentCaptor<Instant> debut = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(dossiers).countEnregistresBetween(debut.capture(), any());
        // 1er avril 2026 00:00 à Ouagadougou (UTC+0)
        assertThat(debut.getValue()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }
}
