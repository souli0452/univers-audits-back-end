package gov.bf.ascelc.univers_audits.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RapportAnnuelPdfServiceTest {

    @Mock private StatistiqueService statistiqueService;

    @InjectMocks
    private RapportAnnuelPdfService service;

    private static String extractText(byte[] pdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                text.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)));
            }
            return text.toString();
        }
    }

    private static StatistiqueResponse.StatistiqueResponseBuilder minimalStatsBuilder() {
        return StatistiqueResponse.builder()
                .period("Année 2026")
                .generatedAt("19/08/2026 10:00")
                .totalDossiers(0)
                .countByStatus(Map.of())
                .countBySubmissionMode(Map.of())
                .countByType(Map.of())
                .inadmissibleCount(0)
                .investigatedCount(0)
                .transferredCount(0)
                .closedWithoutInvestigationCount(0)
                .admissibilityRate(0.0)
                .inProgressInvestigations(0)
                .reportsProduced(0)
                .referredToJustice(0)
                .unfoundedWithoutInvestigation(0)
                .unfoundedAfterInvestigation(0)
                .overdueAcknowledgments(0)
                .overdueInvestigations(0)
                .overdueComplements(0)
                .countByCtadpRecommandation(Map.of())
                .countByCgeDecision(Map.of())
                .countByTargetedPartyType(Map.of())
                .countByInvestigationOutcome(Map.of())
                .investigationCoverageRate(0.0)
                .reportProductionRate(0.0)
                .acknowledgmentCoverageRate(0.0);
    }

    @Test
    void exportRapportAnnuel_genereUnPdfNonVide() {
        when(statistiqueService.getAnnualStats(2026)).thenReturn(minimalStatsBuilder().build());

        byte[] pdf = service.exportRapportAnnuel(2026);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void exportRapportAnnuel_contientLeTitreEtAnnee() throws Exception {
        when(statistiqueService.getAnnualStats(2026)).thenReturn(minimalStatsBuilder().build());

        byte[] pdf = service.exportRapportAnnuel(2026);

        assertThat(extractText(pdf)).contains("RAPPORT ANNUEL D'ACTIVITÉ 2026");
    }

    @Test
    void exportRapportAnnuel_contientLesVolumesEtStatuts() throws Exception {
        StatistiqueResponse stats = minimalStatsBuilder()
                .totalDossiers(150)
                .countByStatus(Map.of("CLOS", 100L, "EN_INVESTIGATION", 50L))
                .totalEstimatedLoss(new BigDecimal("2500000"))
                .build();
        when(statistiqueService.getAnnualStats(2026)).thenReturn(stats);

        byte[] pdf = service.exportRapportAnnuel(2026);

        String text = extractText(pdf);
        assertThat(text).contains("150");
        assertThat(text).contains("Clôturé");
        assertThat(text).contains("Investigation");
    }

    @Test
    void exportRapportAnnuel_contientLesRatiosDeCouverture() throws Exception {
        StatistiqueResponse stats = minimalStatsBuilder()
                .admissibilityRate(91.3)
                .investigationCoverageRate(64.5)
                .reportProductionRate(80.0)
                .acknowledgmentCoverageRate(97.2)
                .build();
        when(statistiqueService.getAnnualStats(2026)).thenReturn(stats);

        byte[] pdf = service.exportRapportAnnuel(2026);

        String text = extractText(pdf);
        assertThat(text).contains("64,5%");
        assertThat(text).contains("80,0%");
        assertThat(text).contains("97,2%");
    }

    @Test
    void exportRapportAnnuel_afficheNonDisponiblePourDelaiNull() throws Exception {
        StatistiqueResponse stats = minimalStatsBuilder()
                .avgRegistrationDelayDays(null)
                .avgOpportunityStudyDays(null)
                .avgAcknowledgmentDays(null)
                .avgInvestigationDurationDays(null)
                .avgLegalAdvisorApprovalDays(null)
                .avgDeiApprovalDays(null)
                .avgCgeaApprovalDays(null)
                .avgCgeApprovalDays(null)
                .avgPlanActionsSubmissionDays(null)
                .build();
        when(statistiqueService.getAnnualStats(2026)).thenReturn(stats);

        byte[] pdf = service.exportRapportAnnuel(2026);

        assertThat(pdf).isNotEmpty();
        assertThat(extractText(pdf)).contains("Non disponible");
    }

    @Test
    void exportRapportAnnuel_genereUnPdfValideMemeSiAucunDossier() throws Exception {
        when(statistiqueService.getAnnualStats(2026)).thenReturn(minimalStatsBuilder().build());

        byte[] pdf = service.exportRapportAnnuel(2026);

        assertThat(pdf).isNotEmpty();
        assertThat(extractText(pdf)).contains("RAPPORT ANNUEL D'ACTIVITÉ 2026");
    }
}
