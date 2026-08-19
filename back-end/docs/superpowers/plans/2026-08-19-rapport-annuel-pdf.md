# Export PDF du rapport annuel d'activité Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un export PDF du rapport annuel d'activité ASCE-LC, restituant en mise en page tabulaire les données déjà calculées par `StatistiqueService.getAnnualStats(int year)`.

**Architecture:** Nouvelle classe `RapportAnnuelPdfService` (iText 7, mise en page programmatique dupliquant le style visuel de `PdfExportService` sans le coupler), exposée via un nouvel endpoint `GET /api/v1/stats/annual/export` sur `StatistiqueController`. Aucune nouvelle requête, aucun nouveau calcul — uniquement une nouvelle représentation de données déjà agrégées.

**Tech Stack:** Java 17, Spring Boot 3, iText 7 (`com.itextpdf:itext7-core`), JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-19-rapport-annuel-pdf-design.md`

## Global Constraints

- `RapportAnnuelPdfService` ne dépend que de `StatistiqueService` — jamais de `PdfExportService`, jamais d'un repository. Aucune nouvelle requête, aucun nouveau calcul.
- Tous les champs `Map<String, Long>` de `StatistiqueResponse` sont garantis non-null par `StatistiqueServiceImpl.buildMap(...)` (retourne toujours au moins une `HashMap` vide) — pas de garde `null` sur ces champs.
- Aucune mention « CONFIDENTIEL » dans ce document (décision de conception explicite — agrégat statistique pur, sans PII).
- L'endpoint utilise `@PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")` — jamais `isAuthenticated()`.
- Formatage des pourcentages et délais avec `Locale.forLanguageTag("fr-FR")` explicite (virgule décimale, cohérent avec le public institutionnel francophone du document, et déterministe indépendamment du `Locale` par défaut de la JVM d'exécution — nouveau choix pour cette classe, pas une modification d'un formatage existant ailleurs).
- Un champ `Double` de délai à `null` s'affiche « Non disponible », jamais une exception ni une chaîne vide.
- Pas de test de contrôleur (convention du dépôt : 0 `*ControllerTest.java` partout ailleurs).

---

### Task 1: `RapportAnnuelPdfService` — génération du PDF

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfService.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfServiceTest.java`

**Interfaces:**
- Consumes: `StatistiqueService.getAnnualStats(int year): StatistiqueResponse` (déjà existant, interface `gov.bf.ascelc.univers_audits.service.StatistiqueService`).
- Produces: `RapportAnnuelPdfService.exportRapportAnnuel(int year): byte[]` — consommé par Task 2.

- [ ] **Step 1: Write the failing test file**

Create `src/test/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfServiceTest.java`:

```java
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
            return PdfTextExtractor.getTextFromPage(pdfDoc.getFirstPage());
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
```

- [ ] **Step 2: Run tests to verify they fail on compilation**

Run: `./mvnw -q -Dtest=RapportAnnuelPdfServiceTest test`
Expected: FAIL — compilation error, `RapportAnnuelPdfService` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfService.java`:

```java
package gov.bf.ascelc.univers_audits.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class RapportAnnuelPdfService {

    private final StatistiqueService statistiqueService;

    private static final DeviceRgb VERT_ASCE  = new DeviceRgb(26,  107, 60);
    private static final DeviceRgb OR_ASCE    = new DeviceRgb(201, 162, 39);
    private static final DeviceRgb GRIS_CLAIR = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb TEXTE_GRIS = new DeviceRgb(100, 100, 100);
    private static final DeviceRgb BLANC      = new DeviceRgb(255, 255, 255);

    private static final Locale FR = Locale.forLanguageTag("fr-FR");

    public byte[] exportRapportAnnuel(int year) {

        StatistiqueResponse stats = statistiqueService.getAnnualStats(year);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addHeader(doc, year, stats, fontBold, fontNormal);
            addVolumesSection(doc, stats, fontBold, fontNormal);
            addTraitementSection(doc, stats, fontBold, fontNormal);
            addPriorisationsSection(doc, stats, fontBold, fontNormal);
            addPartiesViseesSection(doc, stats, fontBold, fontNormal);
            addInvestigationsSection(doc, stats, fontBold, fontNormal);
            addDelaisSection(doc, stats, fontBold, fontNormal);
            addRatiosCouvertureSection(doc, stats, fontBold, fontNormal);
            addDepassementsSection(doc, stats, fontBold, fontNormal);
            addFooter(doc, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur export rapport annuel {}: {}", year, e.getMessage());
            throw new RuntimeException("Erreur génération rapport annuel: " + e.getMessage());
        }
    }

    private void addHeader(Document doc, int year, StatistiqueResponse stats,
                            PdfFont fontBold, PdfFont fontNormal) {

        Table topBar = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setHeight(6)
                .setBackgroundColor(OR_ASCE)
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(0);
        topBar.addCell(new Cell().setBorder(Border.NO_BORDER).add(new Paragraph("")));
        doc.add(topBar);

        Table header = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(VERT_ASCE)
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(20);

        Cell leftCell = new Cell().setBorder(Border.NO_BORDER).setPadding(20);

        leftCell.add(new Paragraph("ASCE-LC")
                .setFont(fontBold).setFontSize(22)
                .setFontColor(ColorConstants.WHITE).setMarginBottom(4));

        leftCell.add(new Paragraph("Autorité Supérieure de Contrôle d'État")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210)).setMarginBottom(2));

        leftCell.add(new Paragraph("et de Lutte contre la Corruption")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210)).setMarginBottom(8));

        leftCell.add(new Paragraph("RAPPORT ANNUEL D'ACTIVITÉ " + year)
                .setFont(fontBold).setFontSize(14).setFontColor(OR_ASCE));

        header.addCell(leftCell);

        Cell rightCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(20)
                .setTextAlignment(TextAlignment.RIGHT);

        rightCell.add(new Paragraph(stats.getPeriod() != null ? stats.getPeriod() : String.valueOf(year))
                .setFont(fontBold).setFontSize(14)
                .setFontColor(ColorConstants.WHITE).setMarginBottom(8));

        rightCell.add(new Paragraph("Généré le "
                + (stats.getGeneratedAt() != null ? stats.getGeneratedAt() : "—"))
                .setFont(fontNormal).setFontSize(9)
                .setFontColor(new DeviceRgb(200, 230, 210)));

        header.addCell(rightCell);
        doc.add(header);
    }

    private void addVolumesSection(Document doc, StatistiqueResponse stats,
                                    PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Volumes et tendances", fontBold));

        Table totals = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(12);
        addInfoCell(totals, "Dossiers reçus", String.valueOf(stats.getTotalDossiers()),
                fontBold, fontNormal);
        addInfoCell(totals, "Préjudice total estimé",
                stats.getTotalEstimatedLoss() != null
                        ? String.format(FR, "%,.0f FCFA", stats.getTotalEstimatedLoss().doubleValue())
                        : "—",
                fontBold, fontNormal);
        doc.add(totals);

        doc.add(subLabel("Répartition par statut", fontBold));
        addLabelCountTable(doc, stats.getCountByStatus(), this::getStatusLabel, fontBold, fontNormal);

        doc.add(subLabel("Répartition par modalité de saisine", fontBold));
        addLabelCountTable(doc, stats.getCountBySubmissionMode(), this::getModeLabel, fontBold, fontNormal);

        doc.add(subLabel("Répartition par type", fontBold));
        addLabelCountTable(doc, stats.getCountByType(), this::getTypeLabel, fontBold, fontNormal);
    }

    private void addTraitementSection(Document doc, StatistiqueResponse stats,
                                       PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("État de traitement", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Dossiers irrecevables",
                String.valueOf(stats.getInadmissibleCount()), fontBold, fontNormal);
        addInfoCell(table, "Dossiers transférés",
                String.valueOf(stats.getTransferredCount()), fontBold, fontNormal);
        addInfoCell(table, "Clôturés sans investigation",
                String.valueOf(stats.getClosedWithoutInvestigationCount()), fontBold, fontNormal);
        addInfoCell(table, "Taux de recevabilité",
                formatPercent(stats.getAdmissibilityRate()), fontBold, fontNormal);

        doc.add(table);
    }

    private void addPriorisationsSection(Document doc, StatistiqueResponse stats,
                                          PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Priorisations et décisions", fontBold));

        doc.add(subLabel("Recommandations CTADP", fontBold));
        addLabelCountTable(doc, stats.getCountByCtadpRecommandation(),
                this::getCtadpRecommandationLabel, fontBold, fontNormal);

        doc.add(subLabel("Décisions CGE", fontBold));
        addLabelCountTable(doc, stats.getCountByCgeDecision(),
                this::getCtadpRecommandationLabel, fontBold, fontNormal);
    }

    private void addPartiesViseesSection(Document doc, StatistiqueResponse stats,
                                          PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Parties visées", fontBold));
        addLabelCountTable(doc, stats.getCountByTargetedPartyType(),
                this::getPartyTypeLabel, fontBold, fontNormal);
    }

    private void addInvestigationsSection(Document doc, StatistiqueResponse stats,
                                           PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Résultats des investigations", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(12);

        addInfoCell(table, "Investigations ouvertes",
                String.valueOf(stats.getInvestigatedCount()), fontBold, fontNormal);
        addInfoCell(table, "En cours",
                String.valueOf(stats.getInProgressInvestigations()), fontBold, fontNormal);
        addInfoCell(table, "Rapports produits",
                String.valueOf(stats.getReportsProduced()), fontBold, fontNormal);
        addInfoCell(table, "Renvoyés au Parquet",
                String.valueOf(stats.getReferredToJustice()), fontBold, fontNormal);
        addInfoCell(table, "Non fondés sans investigation",
                String.valueOf(stats.getUnfoundedWithoutInvestigation()), fontBold, fontNormal);
        addInfoCell(table, "Non fondés après investigation",
                String.valueOf(stats.getUnfoundedAfterInvestigation()), fontBold, fontNormal);

        doc.add(table);

        doc.add(subLabel("Répartition des résultats d'investigation", fontBold));
        addLabelCountTable(doc, stats.getCountByInvestigationOutcome(),
                this::getInvestigationOutcomeLabel, fontBold, fontNormal);
    }

    private void addDelaisSection(Document doc, StatistiqueResponse stats,
                                   PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Délais moyens (jours civils)", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Enregistrement",
                formatDelay(stats.getAvgRegistrationDelayDays()), fontBold, fontNormal);
        addInfoCell(table, "Étude d'opportunité",
                formatDelay(stats.getAvgOpportunityStudyDays()), fontBold, fontNormal);
        addInfoCell(table, "Accusé de réception",
                formatDelay(stats.getAvgAcknowledgmentDays()), fontBold, fontNormal);
        addInfoCell(table, "Durée d'investigation",
                formatDelay(stats.getAvgInvestigationDurationDays()), fontBold, fontNormal);
        addInfoCell(table, "Approbation conseiller juridique",
                formatDelay(stats.getAvgLegalAdvisorApprovalDays()), fontBold, fontNormal);
        addInfoCell(table, "Approbation DEI",
                formatDelay(stats.getAvgDeiApprovalDays()), fontBold, fontNormal);
        addInfoCell(table, "Approbation CGEA",
                formatDelay(stats.getAvgCgeaApprovalDays()), fontBold, fontNormal);
        addInfoCell(table, "Approbation CGE",
                formatDelay(stats.getAvgCgeApprovalDays()), fontBold, fontNormal);
        addInfoCell(table, "Transmission plans d'actions",
                formatDelay(stats.getAvgPlanActionsSubmissionDays()), fontBold, fontNormal);

        doc.add(table);
    }

    private void addRatiosCouvertureSection(Document doc, StatistiqueResponse stats,
                                             PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Ratios de couverture", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Taux de recevabilité",
                formatPercent(stats.getAdmissibilityRate()), fontBold, fontNormal);
        addInfoCell(table, "Couverture investigation",
                formatPercent(stats.getInvestigationCoverageRate()), fontBold, fontNormal);
        addInfoCell(table, "Production de rapports",
                formatPercent(stats.getReportProductionRate()), fontBold, fontNormal);
        addInfoCell(table, "Couverture accusés de réception",
                formatPercent(stats.getAcknowledgmentCoverageRate()), fontBold, fontNormal);

        doc.add(table);
    }

    private void addDepassementsSection(Document doc, StatistiqueResponse stats,
                                         PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Dépassements de délai", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Accusés de réception",
                String.valueOf(stats.getOverdueAcknowledgments()), fontBold, fontNormal);
        addInfoCell(table, "Investigations",
                String.valueOf(stats.getOverdueInvestigations()), fontBold, fontNormal);
        addInfoCell(table, "Compléments",
                String.valueOf(stats.getOverdueComplements()), fontBold, fontNormal);

        doc.add(table);
    }

    private void addFooter(Document doc, PdfFont fontNormal) {

        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(VERT_ASCE, 1))
                .setMarginTop(20)
                .setMarginBottom(8));

        doc.add(new Paragraph(
                "ASCE-LC Burkina Faso | " +
                        AsceLcInstitutionalInfo.ADDRESS + " | " +
                        "Tél: " + AsceLcInstitutionalInfo.PHONE + " | " +
                        "Numéro vert: " + AsceLcInstitutionalInfo.NUMERO_VERT)
                .setFont(fontNormal)
                .setFontSize(8)
                .setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));
    }

    private Paragraph sectionTitle(String title, PdfFont fontBold) {
        return new Paragraph(title)
                .setFont(fontBold)
                .setFontSize(11)
                .setFontColor(VERT_ASCE)
                .setMarginBottom(8)
                .setMarginTop(12)
                .setBorderBottom(new SolidBorder(VERT_ASCE, 1))
                .setPaddingBottom(4);
    }

    private Paragraph subLabel(String text, PdfFont fontBold) {
        return new Paragraph(text)
                .setFont(fontBold)
                .setFontSize(10)
                .setFontColor(TEXTE_GRIS)
                .setMarginBottom(4);
    }

    private void addInfoCell(Table table, String label, String value,
                              PdfFont fontBold, PdfFont fontNormal) {
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(GRIS_CLAIR)
                .setPadding(8)
                .setMargin(2);

        cell.add(new Paragraph(label)
                .setFont(fontBold).setFontSize(8)
                .setFontColor(TEXTE_GRIS).setMarginBottom(2));

        cell.add(new Paragraph(value != null ? value : "—")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(20, 20, 20)));

        table.addCell(cell);
    }

    private void addTableRow(Table table, DeviceRgb bg, PdfFont fontNormal, String... values) {
        for (String value : values) {
            table.addCell(new Cell()
                    .setBackgroundColor(bg)
                    .setBorder(new SolidBorder(new DeviceRgb(220, 220, 220), 0.5f))
                    .setPadding(6)
                    .add(new Paragraph(value != null ? value : "—")
                            .setFont(fontNormal).setFontSize(9)
                            .setFontColor(new DeviceRgb(40, 40, 40))));
        }
    }

    private Cell headerCell(String text, PdfFont fontBold) {
        return new Cell()
                .setBackgroundColor(VERT_ASCE)
                .setBorder(Border.NO_BORDER)
                .setPadding(8)
                .add(new Paragraph(text)
                        .setFont(fontBold).setFontSize(9)
                        .setFontColor(ColorConstants.WHITE));
    }

    private void addLabelCountTable(Document doc, Map<String, Long> data,
                                     Function<String, String> labelFn,
                                     PdfFont fontBold, PdfFont fontNormal) {

        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(12);

        table.addHeaderCell(headerCell("Catégorie", fontBold));
        table.addHeaderCell(headerCell("Nombre", fontBold));

        boolean alternate = false;
        for (Map.Entry<String, Long> entry : new TreeMap<>(data).entrySet()) {
            DeviceRgb bg = alternate ? GRIS_CLAIR : BLANC;
            alternate = !alternate;
            addTableRow(table, bg, fontNormal,
                    labelFn.apply(entry.getKey()), String.valueOf(entry.getValue()));
        }

        doc.add(table);
    }

    private String formatDelay(Double days) {
        return days != null ? String.format(FR, "%.1f j", days) : "Non disponible";
    }

    private String formatPercent(double value) {
        return String.format(FR, "%.1f%%", value);
    }

    private String getStatusLabel(String status) {
        return switch (status) {
            case "SOUMIS"                 -> "Soumis";
            case "RECU"                   -> "Reçu";
            case "EN_ETUDE_OPPORTUNITE"   -> "En étude";
            case "EN_ATTENTE_COMPLEMENT"  -> "Complément";
            case "EN_REVUE_CTADP"         -> "Revue CTADP";
            case "RECEVABLE"              -> "Recevable";
            case "IRRECEVABLE"            -> "Irrecevable";
            case "TRANSFERE"              -> "Transféré";
            case "ORIENTEE_ADMINISTRATIF" -> "Orienté (autorité hiérarchique)";
            case "EN_INVESTIGATION"       -> "Investigation";
            case "RAPPORT_PRODUIT"        -> "Rapport produit";
            case "DECISION_RENDUE"        -> "Décision rendue";
            case "CLOS"                   -> "Clôturé";
            case "CLASSE"                 -> "Classé";
            default                       -> status;
        };
    }

    private String getTypeLabel(String type) {
        return switch (type) {
            case "DENONCIATION" -> "Dénonciation";
            case "PLAINTE"      -> "Plainte";
            case "SIGNALEMENT"  -> "Signalement";
            case "AUTO_SAISINE" -> "Auto-saisine";
            default             -> type;
        };
    }

    private String getModeLabel(String mode) {
        return switch (mode) {
            case "IN_PERSON"     -> "Guichet BRPD";
            case "WEB_FORM"      -> "Formulaire Web";
            case "EMAIL"         -> "Email";
            case "PHONE"         -> "Téléphone";
            case "FAX"           -> "Fax";
            case "GREEN_NUMBER"  -> "Numéro Vert";
            case "AUDIO_COUNTER" -> "Comptoir Audio";
            case "PAPER_FORM"    -> "Formulaire Papier";
            default              -> mode;
        };
    }

    private String getCtadpRecommandationLabel(String value) {
        return switch (value) {
            case "VALIDATION_INVESTIGATION"            -> "Validation investigation";
            case "CLASSEMENT"                           -> "Classement";
            case "TRANSMISSION_INSTITUTION_PARTENAIRE" -> "Transmission à une institution partenaire";
            case "ORIENTATION_ADMINISTRATIVE"           -> "Orientation administrative";
            default                                     -> value;
        };
    }

    private String getPartyTypeLabel(String value) {
        return switch (value) {
            case "PRIVATE_PERSON"   -> "Personne privée";
            case "COMPANY"          -> "Entreprise / société";
            case "PUBLIC_AGENT"     -> "Agent de la fonction publique";
            case "PUBLIC_AUTHORITY" -> "Institution / autorité publique";
            default                 -> value;
        };
    }

    private String getInvestigationOutcomeLabel(String value) {
        return switch (value) {
            case "ADMINISTRATIVE_SANCTIONS" -> "Sanctions administratives";
            case "JUDICIAL_REFERRAL"        -> "Saisine judiciaire";
            case "ARCHIVED"                  -> "Classé sans suite";
            case "PRESS_RELEASE"            -> "Communiqué de presse";
            case "ANNUAL_REPORT"            -> "Rapport annuel d'activité";
            default                          -> value;
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q -Dtest=RapportAnnuelPdfServiceTest test`
Expected: PASS — 6 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfService.java src/test/java/gov/bf/ascelc/univers_audits/service/RapportAnnuelPdfServiceTest.java
git commit -m "feat(stats): add RapportAnnuelPdfService for annual report PDF export"
```

---

### Task 2: Endpoint `GET /api/v1/stats/annual/export`

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/StatistiqueController.java`

**Interfaces:**
- Consumes: `RapportAnnuelPdfService.exportRapportAnnuel(int year): byte[]` (produced by Task 1).

- [ ] **Step 1: Add the dependency and endpoint**

In `src/main/java/gov/bf/ascelc/univers_audits/controller/StatistiqueController.java`, add these imports after the existing `import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;` line:

```java
import gov.bf.ascelc.univers_audits.service.RapportAnnuelPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
```

Add the new field next to the existing `private final StatistiqueService statistiqueService;`:

```java
private final RapportAnnuelPdfService rapportAnnuelPdfService;
```

Add the new endpoint after `getAnnualStats`:

```java
    @GetMapping("/annual/export")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<byte[]> exportAnnualStats(
            @RequestParam int year) {

        if (year < 2020 || year > 2100) {
            throw new BusinessException("Année invalide : " + year);
        }
        log.info("Export PDF rapport annuel {}", year);
        byte[] pdf = rapportAnnuelPdfService.exportRapportAnnuel(year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport-annuel-activite-" + year + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
```

The class uses `@RequiredArgsConstructor` (Lombok) — adding the new `final` field is enough, no manual constructor to write.

- [ ] **Step 2: Compile and run the full test suite**

Run: `./mvnw -q test`
Expected: PASS — all existing tests plus the 6 new `RapportAnnuelPdfServiceTest` tests, 0 failures. This is a compile check for the controller (no controller test exists, per repo convention) and a full-suite non-regression check.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/StatistiqueController.java
git commit -m "feat(stats): expose GET /api/v1/stats/annual/export endpoint"
```
