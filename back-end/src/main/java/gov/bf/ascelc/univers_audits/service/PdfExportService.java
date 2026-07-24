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
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.entity.StatusHistory;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final DossierService dossierService;
    private final StatusHistoryRepository statusHistoryRepository;

    private static final DeviceRgb VERT_ASCE  = new DeviceRgb(26,  107, 60);
    private static final DeviceRgb VERT_CLAIR = new DeviceRgb(240, 249, 244);
    private static final DeviceRgb OR_ASCE    = new DeviceRgb(201, 162, 39);
    private static final DeviceRgb GRIS_CLAIR = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb TEXTE_GRIS = new DeviceRgb(100, 100, 100);
    private static final DeviceRgb BLANC      = new DeviceRgb(255, 255, 255); // ← ajouté

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Africa/Ouagadougou"));

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("Africa/Ouagadougou"));



    public byte[] exportDossier(UUID dossierId) {

        // findById applique le contrôle d'affectation/rôle et le masquage de
        // confidentialité — le PDF ne doit jamais exposer plus que l'API JSON.
        DossierResponse dossier = dossierService.findById(dossierId);

        List<StatusHistory> history =
                statusHistoryRepository.findByDossierIdOrderByChangedAtAsc(dossierId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addHeader(doc, dossier, fontBold, fontNormal);

            addDossierInfo(doc, dossier, fontBold, fontNormal);

            if (dossier.getDeclarant() != null) {
                addDeclarantInfo(doc, dossier, fontBold, fontNormal);
            }

            addDescription(doc, dossier, fontBold, fontNormal);

            if (!history.isEmpty()) {
                addWorkflowHistory(doc, history, fontBold, fontNormal);
            }

            addFooter(doc, dossier, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur export PDF dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération PDF: " + e.getMessage());
        }
    }



    private void addHeader(Document doc, DossierResponse dossier,
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

        Cell leftCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(20);

        leftCell.add(new Paragraph("ASCE-LC")
                .setFont(fontBold)
                .setFontSize(22)
                .setFontColor(ColorConstants.WHITE)
                .setMarginBottom(4));

        leftCell.add(new Paragraph("Autorité Supérieure de Contrôle d'État")
                .setFont(fontNormal)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210))
                .setMarginBottom(2));

        leftCell.add(new Paragraph("et de Lutte contre la Corruption")
                .setFont(fontNormal)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210))
                .setMarginBottom(8));

        leftCell.add(new Paragraph("FICHE DE DOSSIER")
                .setFont(fontBold)
                .setFontSize(14)
                .setFontColor(OR_ASCE));

        header.addCell(leftCell);

        Cell rightCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(20)
                .setTextAlignment(TextAlignment.RIGHT);

        String number = dossier.getNumber() != null ? dossier.getNumber() : "En attente";

        rightCell.add(new Paragraph(number)
                .setFont(fontBold)
                .setFontSize(16)
                .setFontColor(ColorConstants.WHITE)
                .setMarginBottom(8));

        rightCell.add(new Paragraph(getStatusLabel(dossier.getStatus().name()))
                .setFont(fontBold)
                .setFontSize(10)
                .setFontColor(OR_ASCE)
                .setBackgroundColor(new DeviceRgb(15, 74, 40))
                .setPadding(6)
                .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(4)));

        if (dossier.getAccessCode() != null) {
            rightCell.add(new Paragraph("Code B4 : " + dossier.getAccessCode())
                    .setFont(fontNormal)
                    .setFontSize(9)
                    .setFontColor(new DeviceRgb(180, 220, 195))
                    .setMarginTop(8));
        }

        header.addCell(rightCell);
        doc.add(header);
    }



    private void addDossierInfo(Document doc, DossierResponse dossier,
                                PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Informations du Dossier", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Type",
                getTypeLabel(dossier.getType() != null ? dossier.getType().name() : ""),
                fontBold, fontNormal);

        addInfoCell(table, "Canal",
                getModeLabel(dossier.getSubmissionMode() != null
                        ? dossier.getSubmissionMode().name() : ""),
                fontBold, fontNormal);

        addInfoCell(table, "Date de création",
                dossier.getCreatedAt() != null ? FMT.format(dossier.getCreatedAt()) : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Date de réception",
                dossier.getReceptionDate() != null
                        ? FMT_DATE.format(dossier.getReceptionDate()) : "—",
                fontBold, fontNormal);

        if (dossier.getIncidentLocation() != null) {
            addInfoCell(table, "Lieu des faits",
                    dossier.getIncidentLocation(), fontBold, fontNormal);
        }

        if (dossier.getIncidentPeriod() != null) {
            addInfoCell(table, "Période",
                    dossier.getIncidentPeriod(), fontBold, fontNormal);
        }

        if (dossier.getEstimatedLoss() != null) {
            addInfoCell(table, "Montant estimé",
                    String.format("%,.0f FCFA", dossier.getEstimatedLoss().doubleValue()),
                    fontBold, fontNormal);
        }

        if (dossier.getClosingDate() != null) {
            addInfoCell(table, "Date de clôture",
                    FMT_DATE.format(dossier.getClosingDate()), fontBold, fontNormal);
        }

        doc.add(table);
    }



    private void addDeclarantInfo(Document doc, DossierResponse dossier,
                                  PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Déclarant", fontBold));

        DeclarantResponse declarant = dossier.getDeclarant();

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        if (Boolean.TRUE.equals(declarant.getAnonymous())) {

            Cell cell = new Cell(1, 4)
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(new DeviceRgb(255, 248, 220))
                    .setPadding(10)
                    .add(new Paragraph(
                            "⚠ Déclarant anonyme — informations masquées")
                            .setFont(fontNormal)
                            .setFontSize(10)
                            .setFontColor(new DeviceRgb(150, 100, 0)));
            table.addCell(cell);

        } else {

            String nom = ((declarant.getFirstName() != null
                    ? declarant.getFirstName() : "") + " " +
                    (declarant.getLastName() != null
                            ? declarant.getLastName() : "")).trim();

            if (!nom.isEmpty()) {
                addInfoCell(table, "Nom complet", nom, fontBold, fontNormal);
            }
            if (declarant.getEmail() != null) {
                addInfoCell(table, "Email", declarant.getEmail(), fontBold, fontNormal);
            }
            if (declarant.getPhoneNumber() != null) {
                addInfoCell(table, "Téléphone",
                        declarant.getPhoneNumber(), fontBold, fontNormal);
            }
            if (declarant.getCommune() != null) {
                addInfoCell(table, "Commune",
                        declarant.getCommune(), fontBold, fontNormal);
            }
            if (declarant.getProvince() != null) {
                addInfoCell(table, "Province",
                        declarant.getProvince(), fontBold, fontNormal);
            }
            if (Boolean.TRUE.equals(declarant.getProtectionRequested())) {
                Cell protCell = new Cell(1, 2)
                        .setBorder(Border.NO_BORDER)
                        .setBackgroundColor(new DeviceRgb(219, 234, 254))
                        .setPadding(8)
                        .add(new Paragraph(
                                "Protection lanceur d'alerte demandée (Loi N°010-2004/AN)")
                                .setFont(fontBold)
                                .setFontSize(9)
                                .setFontColor(new DeviceRgb(30, 64, 175)));
                table.addCell(protCell);
            }
        }

        doc.add(table);
    }


    private void addDescription(Document doc, DossierResponse dossier,
                                PdfFont fontBold, PdfFont fontNormal) {

        if (dossier.getObject() != null) {
            doc.add(sectionTitle("Objet du signalement", fontBold));
            doc.add(new Paragraph(dossier.getObject())
                    .setFont(fontBold)
                    .setFontSize(12)
                    .setFontColor(VERT_ASCE)
                    .setMarginBottom(12)
                    .setPaddingLeft(12)
                    .setBorderLeft(new SolidBorder(VERT_ASCE, 3)));
        }

        if (dossier.getDescription() != null) {
            doc.add(sectionTitle("Description des faits", fontBold));
            doc.add(new Paragraph(dossier.getDescription())
                    .setFont(fontNormal)
                    .setFontSize(10)
                    .setFontColor(new DeviceRgb(40, 40, 40))
                    .setBackgroundColor(GRIS_CLAIR)
                    .setPadding(12)
                    .setMarginBottom(16)
                    .setFirstLineIndent(20));
        }
    }


    private void addWorkflowHistory(Document doc,
                                    List<StatusHistory> history,
                                    PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Historique du Workflow", fontBold));

        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 3}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);


        String[] headers = {"Date", "Statut précédent", "Nouveau statut", "Agent / Motif"};
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .setBackgroundColor(VERT_ASCE)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(8)
                    .add(new Paragraph(h)
                            .setFont(fontBold)
                            .setFontSize(9)
                            .setFontColor(ColorConstants.WHITE)));
        }


        boolean alternate = false;
        for (StatusHistory h : history) {
            DeviceRgb bg = alternate ? GRIS_CLAIR : BLANC; // ← corrigé ici
            alternate = !alternate;

            String date  = h.getChangedAt() != null
                    ? FMT.format(h.getChangedAt()) : "—";
            String prev  = h.getPreviousStatus() != null
                    ? getStatusLabel(h.getPreviousStatus().name()) : "—";
            String next  = h.getNewStatus() != null
                    ? getStatusLabel(h.getNewStatus().name()) : "—";
            String agent = h.getAgentFullName() != null
                    ? h.getAgentFullName() : "Système";

            if (h.getReason() != null && !h.getReason().isBlank()) {
                agent += "\n" + h.getReason();
            }

            addTableRow(table, bg, fontNormal, date, prev, next, agent);
        }

        doc.add(table);
    }



    private void addFooter(Document doc, DossierResponse dossier,
                           PdfFont fontBold, PdfFont fontNormal) {

        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(VERT_ASCE, 1))
                .setMarginTop(20)
                .setMarginBottom(8));

        doc.add(new Paragraph(
                "Document confidentiel — ASCE-LC Burkina Faso | " +
                        "03 BP 7204 Ouagadougou 03 | " +
                        "Tél: +226 25 36 62 62 | " +
                        "Numéro vert: 80 00 11 57")
                .setFont(fontNormal)
                .setFontSize(8)
                .setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));

        String generated = "Document généré le " + FMT.format(java.time.Instant.now());
        if (dossier.getNumber() != null) {
            generated += " — Réf: " + dossier.getNumber();
        }

        doc.add(new Paragraph(generated)
                .setFont(fontNormal)
                .setFontSize(7)
                .setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER));
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

    private void addInfoCell(Table table, String label, String value,
                             PdfFont fontBold, PdfFont fontNormal) {
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(GRIS_CLAIR)
                .setPadding(8)
                .setMargin(2);

        cell.add(new Paragraph(label)
                .setFont(fontBold)
                .setFontSize(8)
                .setFontColor(TEXTE_GRIS)
                .setMarginBottom(2));

        cell.add(new Paragraph(value != null ? value : "—")
                .setFont(fontNormal)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(20, 20, 20)));

        table.addCell(cell);
    }

    private void addTableRow(Table table, DeviceRgb bg,
                             PdfFont fontNormal, String... values) {
        for (String value : values) {
            table.addCell(new Cell()
                    .setBackgroundColor(bg)
                    .setBorder(new SolidBorder(new DeviceRgb(220, 220, 220), 0.5f))
                    .setPadding(6)
                    .add(new Paragraph(value != null ? value : "—")
                            .setFont(fontNormal)
                            .setFontSize(9)
                            .setFontColor(new DeviceRgb(40, 40, 40))));
        }
    }



    private String getStatusLabel(String status) {
        return switch (status) {
            case "SOUMIS"                -> "Soumis";
            case "RECU"                  -> "Reçu";
            case "EN_ETUDE_OPPORTUNITE"  -> "En étude";
            case "EN_ATTENTE_COMPLEMENT" -> "Complément";
            case "EN_REVUE_CTADP"        -> "Revue CTADP";
            case "RECEVABLE"             -> "Recevable";
            case "IRRECEVABLE"           -> "Irrecevable";
            case "TRANSFERE"             -> "Transféré";
            case "EN_INVESTIGATION"      -> "Investigation";
            case "RAPPORT_PRODUIT"       -> "Rapport produit";
            case "DECISION_RENDUE"       -> "Décision rendue";
            case "CLOS"                  -> "Clôturé";
            case "CLASSE"                -> "Classé";
            default                      -> status;
        };
    }

    private String getTypeLabel(String type) {
        return switch (type) {
            case "COMPLAINT"    -> "Plainte";
            case "DENUNCIATION" -> "Dénonciation";
            case "AUTO_REFERRAL"-> "Auto-saisine";
            case "ANONYMOUS"    -> "Anonyme";
            default             -> type;
        };
    }

    private String getModeLabel(String mode) {
        return switch (mode) {
            case "IN_PERSON"    -> "Guichet BRPD";
            case "WEB_FORM"     -> "Formulaire Web";
            case "EMAIL"        -> "Email";
            case "PHONE"        -> "Téléphone";
            case "GREEN_NUMBER" -> "Numéro Vert";
            case "AUDIO_COUNTER"-> "Comptoir Audio";
            case "PAPER_FORM"   -> "Formulaire Papier";
            default             -> mode;
        };
    }
}