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
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpDossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Documents officiels produits à partir du workflow PGPD_GU V3 : convocation du comité (étape 7),
 * et, à venir, quitus du CGE (étape 9) et lettre d'information du plaignant (étape 16).
 *
 * <p>Le texte est un modèle provisoire, à remplacer par les modèles Word des utilisateurs.
 * Les documents sont générés pré-remplis ; la signature reste manuelle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentOfficielPdfService {

    private final SeanceCtadpService seanceCtadpService;

    private static final DeviceRgb VERT_ASCE  = new DeviceRgb(26, 107, 60);
    private static final DeviceRgb OR_ASCE    = new DeviceRgb(201, 162, 39);
    private static final DeviceRgb GRIS_CLAIR = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb TEXTE_GRIS = new DeviceRgb(100, 100, 100);

    private static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");
    private static final DateTimeFormatter FMT_DATE_LONGUE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH).withZone(ZONE);
    private static final DateTimeFormatter FMT_HEURE =
            DateTimeFormatter.ofPattern("HH 'h' mm", Locale.FRENCH).withZone(ZONE);
    private static final DateTimeFormatter FMT_DATE_COURTE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZONE);

    /** Convocation des membres du CTADP à une séance planifiée (étape 7 du workflow). */
    public byte[] convocationCtadp(UUID seanceId) {
        SeanceCtadpResponse seance = seanceCtadpService.findById(seanceId);

        if (seance.getStatut() != StatutSeanceCtadp.PLANIFIEE) {
            throw new BusinessException(
                    "La convocation n'est disponible que pour une séance planifiée");
        }
        if (seance.getDossiers() == null || seance.getDossiers().isEmpty()) {
            throw new BusinessException(
                    "Ajoutez au moins un dossier à la séance avant de produire la convocation");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);
            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont normal = PdfFontFactory.createFont("Helvetica");

            entete(doc, "CONVOCATION DU COMITÉ (CTADP)", bold, normal);

            doc.add(new Paragraph("Ouagadougou, le " + FMT_DATE_COURTE.format(Instant.now()))
                    .setFont(normal).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)
                    .setMarginBottom(16));

            doc.add(new Paragraph("Objet : convocation à la session du Comité de traitement et d'analyse "
                    + "des dénonciations et des plaintes (CTADP)")
                    .setFont(bold).setFontSize(11).setMarginBottom(14));

            Instant date = seance.getDateSeance();
            doc.add(new Paragraph("Mesdames et Messieurs les membres du CTADP sont convoqués à la session "
                    + "qui se tiendra le " + FMT_DATE_LONGUE.format(date)
                    + " à " + FMT_HEURE.format(date) + ".")
                    .setFont(normal).setFontSize(11).setMarginBottom(14));

            List<String> participants = participants(seance.getParticipants());
            if (!participants.isEmpty()) {
                doc.add(titreSection("Membres convoqués", bold));
                for (String p : participants) {
                    doc.add(new Paragraph("•  " + p).setFont(normal).setFontSize(10).setMarginBottom(2));
                }
            }

            doc.add(titreSection("Ordre du jour — dossiers à examiner", bold));
            doc.add(tableDossiers(seance.getDossiers(), bold, normal));

            doc.add(new Paragraph("Le comité étudiera ces dossiers et formulera, pour chacun, "
                    + "son avis et la suite à donner au Contrôleur Général d'État.")
                    .setFont(normal).setFontSize(10).setMarginTop(14).setMarginBottom(24));

            signature(doc, "Le Contrôleur Général d'État Adjoint", bold, normal);
            pied(doc, bold, normal);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur génération convocation CTADP séance {}: {}", seanceId, e.getMessage());
            throw new RuntimeException("Erreur génération convocation: " + e.getMessage());
        }
    }

    // ── Éléments communs ─────────────────────────────────────────────────

    private void entete(Document doc, String titre, PdfFont bold, PdfFont normal) {
        Table barre = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100)).setHeight(6)
                .setBackgroundColor(OR_ASCE).setBorder(Border.NO_BORDER);
        barre.addCell(new Cell().setBorder(Border.NO_BORDER).add(new Paragraph("")));
        doc.add(barre);

        Table entete = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(VERT_ASCE).setBorder(Border.NO_BORDER).setMarginBottom(20);
        Cell cell = new Cell().setBorder(Border.NO_BORDER).setPadding(20);
        cell.add(new Paragraph("ASCE-LC").setFont(bold).setFontSize(22)
                .setFontColor(ColorConstants.WHITE).setMarginBottom(4));
        cell.add(new Paragraph("Autorité Supérieure de Contrôle d'État et de Lutte contre la Corruption")
                .setFont(normal).setFontSize(10)
                .setFontColor(new DeviceRgb(200, 230, 210)).setMarginBottom(8));
        cell.add(new Paragraph(titre).setFont(bold).setFontSize(14).setFontColor(OR_ASCE));
        entete.addCell(cell);
        doc.add(entete);
    }

    private Paragraph titreSection(String titre, PdfFont bold) {
        return new Paragraph(titre).setFont(bold).setFontSize(11).setFontColor(VERT_ASCE)
                .setMarginTop(8).setMarginBottom(6);
    }

    private Table tableDossiers(List<SeanceCtadpDossierResponse> dossiers, PdfFont bold, PdfFont normal) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 4}))
                .setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(celluleEntete("N° du dossier", bold));
        table.addHeaderCell(celluleEntete("Objet", bold));
        for (SeanceCtadpDossierResponse d : dossiers) {
            table.addCell(cellule(d.getDossierNumber() != null ? d.getDossierNumber() : "En attente", normal));
            table.addCell(cellule(d.getDossierObject() != null ? d.getDossierObject() : "—", normal));
        }
        return table;
    }

    private Cell celluleEntete(String texte, PdfFont bold) {
        return new Cell().setBackgroundColor(GRIS_CLAIR).setPadding(6)
                .add(new Paragraph(texte).setFont(bold).setFontSize(9));
    }

    private Cell cellule(String texte, PdfFont normal) {
        return new Cell().setPadding(6).add(new Paragraph(texte).setFont(normal).setFontSize(9));
    }

    private void signature(Document doc, String signataire, PdfFont bold, PdfFont normal) {
        doc.add(new Paragraph(signataire).setFont(bold).setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT).setMarginBottom(40));
        doc.add(new Paragraph("Signature et cachet").setFont(normal).setFontSize(8)
                .setFontColor(TEXTE_GRIS).setTextAlignment(TextAlignment.RIGHT));
    }

    private void pied(Document doc, PdfFont bold, PdfFont normal) {
        doc.add(new Paragraph().setBorderTop(new SolidBorder(VERT_ASCE, 1))
                .setMarginTop(24).setMarginBottom(8));
        doc.add(new Paragraph("Adresse postale : " + AsceLcInstitutionalInfo.ADDRESS)
                .setFont(normal).setFontSize(8).setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Tél. : " + AsceLcInstitutionalInfo.PHONE
                + " - E-mail : " + AsceLcInstitutionalInfo.EMAIL_INFO
                + " - Site web : " + AsceLcInstitutionalInfo.WEBSITE
                + " - Numéro vert : " + AsceLcInstitutionalInfo.NUMERO_VERT)
                .setFont(normal).setFontSize(8).setFontColor(TEXTE_GRIS)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(AsceLcInstitutionalInfo.SLOGAN).setFont(bold).setFontSize(8)
                .setFontColor(VERT_ASCE).setTextAlignment(TextAlignment.CENTER).setMarginTop(4));
    }

    /** Découpe la liste des participants saisie en texte libre (un par ligne ou séparés par « ; »). */
    static List<String> participants(String saisie) {
        if (saisie == null || saisie.isBlank()) return List.of();
        return Arrays.stream(saisie.split("[\\r\\n;]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
