package gov.bf.ascelc.univers_audits.service;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.io.image.ImageDataFactory;
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
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationSummaryResponse;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.model.entity.StatusHistory;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final FicheAffectationRepository ficheAffectationRepository;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private static final DeviceRgb VERT_ASCE  = new DeviceRgb(26,  107, 60);
    private static final DeviceRgb VERT_CLAIR = new DeviceRgb(240, 249, 244);
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



    public byte[] exportRecepisse(UUID dossierId) {

        // findById applique le contrôle d'affectation/rôle et le masquage de
        // confidentialité — le récépissé ne doit jamais exposer plus que
        // l'API JSON, exactement comme exportDossier.
        DossierResponse dossier = dossierService.findById(dossierId);
        return buildRecepissePdf(dossier);
    }

    /**
     * Variante publique, pour le portail citoyen : findByAccessCode ne
     * contrôle aucun rôle agent (le code de suivi B4 fait office de preuve
     * de propriété, comme pour le suivi public) mais masque déjà
     * étudeOpportunite/decisionCGE — absents du récépissé, donc sans impact
     * ici. Ne pas réutiliser ce chemin pour l'accusé de réception, qui a
     * besoin de decisionCGE.
     */
    public byte[] exportRecepisseByAccessCode(String accessCode) {
        DossierResponse dossier = dossierService.findByAccessCode(accessCode);
        return buildRecepissePdf(dossier);
    }

    private byte[] buildRecepissePdf(DossierResponse dossier) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addRecepisseHeader(doc, dossier, fontBold, fontNormal);
            addRecepisseBody(doc, dossier, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur export récépissé dossier {}: {}", dossier.getId(), e.getMessage());
            throw new RuntimeException("Erreur génération récépissé: " + e.getMessage());
        }
    }

    public byte[] exportAccuseReception(UUID dossierId) {

        // findById applique le contrôle d'affectation/rôle et le masquage de
        // confidentialité — même garde que exportRecepisse/exportDossier.
        DossierResponse dossier = dossierService.findById(dossierId);

        if (dossier.getDecisionCGE() == null) {
            throw new BusinessException(
                    "Aucune décision CGE n'a encore été rendue pour ce dossier");
        }
        RecommandationCtadp decision = dossier.getDecisionCGE().getDecision();
        if (decision == RecommandationCtadp.CLASSEMENT) {
            throw new BusinessException(
                    "Ce dossier a été classé — utilisez l'export de la réponse motivée, "
                            + "pas l'accusé de réception");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addDecisionHeader(doc, dossier, fontBold, fontNormal,
                    "Accusé de réception", "Suite donnée à votre dossier – document confidentiel");
            addAccuseReceptionBody(doc, dossier, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur export accusé de réception dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération accusé de réception: " + e.getMessage());
        }
    }

    public byte[] exportReponseMotivee(UUID dossierId) {

        DossierResponse dossier = dossierService.findById(dossierId);

        if (dossier.getDecisionCGE() == null) {
            throw new BusinessException(
                    "Aucune décision CGE n'a encore été rendue pour ce dossier");
        }
        if (dossier.getDecisionCGE().getDecision() != RecommandationCtadp.CLASSEMENT) {
            throw new BusinessException(
                    "Ce dossier n'a pas été classé — utilisez l'export de l'accusé de "
                            + "réception, pas la réponse motivée");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addDecisionHeader(doc, dossier, fontBold, fontNormal,
                    "Réponse motivée", "Classement de votre dossier – document confidentiel");
            addReponseMotiveeBody(doc, dossier, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur export réponse motivée dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération réponse motivée: " + e.getMessage());
        }
    }

    /**
     * Document interne de synthèse à l'archivage, distinct des trois
     * courriers citoyens ci-dessus (récépissé/accusé/réponse motivée) :
     * une page condensant tout le cycle de vie du dossier (décision,
     * affectation, investigation, motif de clôture) pour un agent qui
     * n'a pas suivi l'affaire depuis le début.
     */
    public byte[] exportResumeCloture(UUID dossierId) {

        DossierResponse dossier = dossierService.findById(dossierId);

        if (dossier.getStatus() != gov.bf.ascelc.univers_audits.enums.DossierStatus.CLOS
                && dossier.getStatus() != gov.bf.ascelc.univers_audits.enums.DossierStatus.CLASSE) {
            throw new BusinessException(
                    "Le résumé de clôture n'est disponible qu'une fois le dossier clos ou classé");
        }

        StatusHistory dernierMouvement = statusHistoryRepository
                .findFirstByDossierIdOrderByChangedAtDesc(dossierId)
                .orElse(null);

        FicheAffectation fiche = ficheAffectationRepository
                .findByDossierId(dossierId)
                .orElse(null);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            PdfFont fontBold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal = PdfFontFactory.createFont("Helvetica");

            addHeader(doc, dossier, fontBold, fontNormal);
            addResumeClotureBody(doc, dossier, dernierMouvement, fiche, fontBold, fontNormal);
            addRecepisseFooter(doc, fontBold, fontNormal);

            doc.close();
            return baos.toByteArray();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur export résumé de clôture dossier {}: {}", dossierId, e.getMessage());
            throw new RuntimeException("Erreur génération résumé de clôture: " + e.getMessage());
        }
    }

    private void addResumeClotureBody(Document doc, DossierResponse dossier,
                                      StatusHistory dernierMouvement, FicheAffectation fiche,
                                      PdfFont fontBold, PdfFont fontNormal) {

        doc.add(sectionTitle("Synthèse du dossier", fontBold));

        Table infos = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(12);

        addInfoCell(infos, "Type", getTypeLabel(dossier.getType() != null ? dossier.getType().name() : ""),
                fontBold, fontNormal);
        addInfoCell(infos, "Canal de réception",
                getModeLabel(dossier.getSubmissionMode() != null ? dossier.getSubmissionMode().name() : ""),
                fontBold, fontNormal);

        DeclarantResponse declarant = dossier.getDeclarant();
        String deposantLabel;
        if (declarant == null) {
            deposantLabel = "— (auto-saisine)";
        } else if (Boolean.TRUE.equals(dossier.getAnonymous())) {
            deposantLabel = "Anonyme";
        } else if (declarant.getDisplayName() != null && !declarant.getDisplayName().isBlank()) {
            deposantLabel = declarant.getDisplayName();
        } else {
            deposantLabel = "—";
        }
        addInfoCell(infos, "Déclarant", deposantLabel, fontBold, fontNormal);
        addInfoCell(infos, "Objet", dossier.getObject() != null ? dossier.getObject() : "—",
                fontBold, fontNormal);

        addInfoCell(infos, "Date de soumission",
                dossier.getCreatedAt() != null ? FMT_DATE.format(dossier.getCreatedAt()) : "—",
                fontBold, fontNormal);
        addInfoCell(infos, "Date de réception",
                dossier.getReceptionDate() != null ? FMT_DATE.format(dossier.getReceptionDate()) : "—",
                fontBold, fontNormal);
        addInfoCell(infos, "Date de clôture",
                dossier.getClosingDate() != null ? FMT_DATE.format(dossier.getClosingDate()) : "—",
                fontBold, fontNormal);

        String duree = "—";
        if (dossier.getCreatedAt() != null && dossier.getClosingDate() != null) {
            long jours = java.time.Duration.between(
                    dossier.getCreatedAt(), dossier.getClosingDate()).toDays();
            duree = jours + " jour(s)";
        }
        addInfoCell(infos, "Durée totale de traitement", duree, fontBold, fontNormal);

        doc.add(infos);

        doc.add(sectionTitle("Décision finale", fontBold));
        if (dossier.getDecisionCGE() != null) {
            Table decision = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(12);
            addInfoCell(decision, "Recommandation CTADP",
                    getRecommandationLabel(dossier.getDecisionCGE().getDecision()),
                    fontBold, fontNormal);
            addInfoCell(decision, "Date de décision",
                    dossier.getDecisionCGE().getDateDecision() != null
                            ? FMT_DATE.format(dossier.getDecisionCGE().getDateDecision()) : "—",
                    fontBold, fontNormal);
            doc.add(decision);
            String motifDecision = dossier.getDecisionCGE().getMotif();
            if (motifDecision != null && !motifDecision.isBlank()) {
                doc.add(new Paragraph(motifDecision)
                        .setFont(fontNormal).setFontSize(10)
                        .setFontColor(new DeviceRgb(40, 40, 40)).setMarginBottom(12));
            }
        } else {
            doc.add(new Paragraph("Aucune décision CGE enregistrée.")
                    .setFont(fontNormal).setFontSize(10)
                    .setFontColor(TEXTE_GRIS).setMarginBottom(12));
        }

        if (fiche != null) {
            doc.add(sectionTitle("Affectation", fontBold));
            Table aff = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(12);
            String cible = "—";
            if (fiche.getTypeDesignation() != null) {
                cible = switch (fiche.getTypeDesignation()) {
                    case DEPARTEMENT -> fiche.getDepartementDesigne() != null
                            ? fiche.getDepartementDesigne().getLibelle() : "Département";
                    case AGENT_CJ -> fiche.getAgentDesigne() != null
                            ? fiche.getAgentDesigne().getNomComplet() : "Conseiller juridique";
                    case BRPD -> "BRPD";
                };
            }
            addInfoCell(aff, "Affecté à", cible, fontBold, fontNormal);
            addInfoCell(aff, "État d'avancement final",
                    fiche.getEtatAvancement() != null
                            ? switch (fiche.getEtatAvancement()) {
                                case EN_COURS -> "En cours";
                                case CLOTURE  -> "Clôturé";
                                case AUTRE    -> fiche.getEtatAvancementPrecision() != null
                                        ? fiche.getEtatAvancementPrecision() : "Autre";
                            }
                            : "—",
                    fontBold, fontNormal);
            doc.add(aff);
        }

        InvestigationSummaryResponse investigation = dossier.getInvestigation();
        if (investigation != null) {
            doc.add(sectionTitle("Investigation", fontBold));
            Table inv = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(12);
            addInfoCell(inv, "Statut",
                    investigation.getStatus() != null ? investigation.getStatus().name() : "—",
                    fontBold, fontNormal);
            addInfoCell(inv, "Équipe", investigation.getMemberCount() + " membre(s)",
                    fontBold, fontNormal);
            doc.add(inv);
        }

        doc.add(sectionTitle("Motif de clôture", fontBold));
        String motifCloture = dernierMouvement != null && dernierMouvement.getReason() != null
                && !dernierMouvement.getReason().isBlank()
                ? dernierMouvement.getReason() : "Non renseigné.";
        doc.add(new Paragraph(motifCloture)
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40)));
    }

    private String getRecommandationLabel(RecommandationCtadp r) {
        if (r == null) return "—";
        return switch (r) {
            case VALIDATION_INVESTIGATION -> "Validation investigation";
            case CLASSEMENT -> "Classement sans suite";
            case TRANSMISSION_INSTITUTION_PARTENAIRE -> "Transmission à une institution partenaire";
            case ORIENTATION_ADMINISTRATIVE -> "Orientation administrative";
        };
    }

    private void addRecepisseHeader(Document doc, DossierResponse dossier,
                                    PdfFont fontBold, PdfFont fontNormal) {

        // En-tête sobre : logo de l'ASCE-LC à gauche, lieu et date à droite, sans bandeau coloré.
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        try (java.io.InputStream in = new org.springframework.core.io.ClassPathResource("pdf/logo-asce.png").getInputStream()) {
            logoCell.add(new Image(ImageDataFactory.create(in.readAllBytes())).setHeight(46));
        } catch (Exception e) {
            log.warn("Logo ASCE-LC introuvable pour le récépissé : {}", e.getMessage());
            logoCell.add(new Paragraph("ASCE-LC").setFont(fontBold).setFontSize(20).setFontColor(VERT_ASCE));
        }
        header.addCell(logoCell);

        java.time.Instant date = dossier.getReceptionDate() != null
                ? dossier.getReceptionDate() : java.time.Instant.now();
        header.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph("Ouagadougou, le " + FMT_DATE.format(date))
                        .setFont(fontNormal).setFontSize(10).setFontColor(TEXTE_GRIS)));
        doc.add(header);
    }

    private void addDecisionHeader(Document doc, DossierResponse dossier,
                                   PdfFont fontBold, PdfFont fontNormal,
                                   String titre, String sousTitre) {
        addRecepisseHeader(doc, dossier, fontBold, fontNormal);
        addTitreDocument(doc, titre, sousTitre, fontBold, fontNormal);
    }

    /** Titre de document sobre : titre vert, ligne de contexte grise dessous. */
    private void addTitreDocument(Document doc, String titre, String sousTitre,
                                  PdfFont fontBold, PdfFont fontNormal) {
        doc.add(new Paragraph(titre)
                .setFont(fontBold).setFontSize(22).setFontColor(VERT_ASCE)
                .setMarginTop(34).setMarginBottom(2));
        doc.add(new Paragraph(sousTitre)
                .setFont(fontNormal).setFontSize(10).setFontColor(TEXTE_GRIS)
                .setMarginBottom(24));
    }

    private void addAccuseReceptionBody(Document doc, DossierResponse dossier,
                                        PdfFont fontBold, PdfFont fontNormal) {

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Numéro d'enregistrement",
                dossier.getNumber() != null ? dossier.getNumber() : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Date de la décision",
                dossier.getDecisionCGE().getDateDecision() != null
                        ? FMT_DATE.format(dossier.getDecisionCGE().getDateDecision()) : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Décision",
                getStatusLabel(dossier.getStatus() != null ? dossier.getStatus().name() : ""),
                fontBold, fontNormal);

        addInfoCell(table, "Code de suivi",
                dossier.getAccessCode() != null ? dossier.getAccessCode() : "—",
                fontBold, fontNormal);

        doc.add(table);

        doc.add(new Paragraph(
                "L'ASCE-LC accuse réception de votre dossier et vous informe que la suite "
                        + "suivante lui a été donnée par le Contrôleur Général d'État. "
                        + "Conservez le code de suivi ci-dessus pour toute correspondance "
                        + "ultérieure.")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40))
                .setMarginTop(8).setMarginBottom(12));

        String motif = dossier.getDecisionCGE().getMotif();
        if (motif != null && !motif.isBlank()) {
            doc.add(new Paragraph("Observations")
                    .setFont(fontBold).setFontSize(11)
                    .setFontColor(VERT_ASCE).setMarginBottom(4));
            doc.add(new Paragraph(motif)
                    .setFont(fontNormal).setFontSize(10)
                    .setFontColor(new DeviceRgb(40, 40, 40)));
        }
    }

    private void addReponseMotiveeBody(Document doc, DossierResponse dossier,
                                       PdfFont fontBold, PdfFont fontNormal) {

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);

        addInfoCell(table, "Numéro d'enregistrement",
                dossier.getNumber() != null ? dossier.getNumber() : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Date de la décision",
                dossier.getDecisionCGE().getDateDecision() != null
                        ? FMT_DATE.format(dossier.getDecisionCGE().getDateDecision()) : "—",
                fontBold, fontNormal);

        addInfoCell(table, "Code de suivi",
                dossier.getAccessCode() != null ? dossier.getAccessCode() : "—",
                fontBold, fontNormal);

        doc.add(table);

        doc.add(new Paragraph(
                "Après examen, l'ASCE-LC vous informe que votre dossier a été classé sans "
                        + "suite, pour les motifs exposés ci-dessous.")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40))
                .setMarginTop(8).setMarginBottom(12));

        doc.add(new Paragraph("Motifs du classement")
                .setFont(fontBold).setFontSize(11)
                .setFontColor(VERT_ASCE).setMarginBottom(4));

        String motif = dossier.getDecisionCGE().getMotif();
        doc.add(new Paragraph(motif != null && !motif.isBlank() ? motif : "—")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40)));
    }

    private void addRecepisseBody(Document doc, DossierResponse dossier,
                                  PdfFont fontBold, PdfFont fontNormal) {

        addTitreDocument(doc, "Récépissé de dépôt",
                "Dénonciation ou plainte – document confidentiel", fontBold, fontNormal);

        DeclarantResponse declarant = dossier.getDeclarant();
        String deposantLabel;
        if (declarant == null) {
            deposantLabel = "—";
        } else if (Boolean.TRUE.equals(dossier.getAnonymous())) {
            deposantLabel = "Anonyme";
        } else {
            String nomComplet = ((declarant.getFirstName() != null ? declarant.getFirstName() : "")
                    + " " + (declarant.getLastName() != null ? declarant.getLastName() : "")).trim();
            if (!nomComplet.isEmpty()) {
                deposantLabel = nomComplet;
            } else if (declarant.getDisplayName() != null && !declarant.getDisplayName().isBlank()) {
                // Identité masquée pour cause de protection lanceur d'alerte —
                // cf. DossierServiceImpl.maskSensitiveData, qui pose un
                // libellé standard à la place du nom/prénom (nullifiés).
                deposantLabel = declarant.getDisplayName();
            } else {
                deposantLabel = "—";
            }
        }

        // Liste « libellé / valeur » sans quadrillage, façon formulaire administratif.
        Table infos = new Table(UnitValue.createPercentArray(new float[]{34, 66}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(22);
        addRecepisseLine(infos, "Numéro d'enregistrement",
                dossier.getNumber() != null ? dossier.getNumber() : "—", fontBold, fontNormal);
        addRecepisseLine(infos, "Date et heure de réception",
                dossier.getReceptionDate() != null ? FMT.format(dossier.getReceptionDate()) : "—",
                fontBold, fontNormal);
        addRecepisseLine(infos, "Mode de réception",
                getModeLabel(dossier.getSubmissionMode() != null
                        ? dossier.getSubmissionMode().name() : ""),
                fontBold, fontNormal);
        addRecepisseLine(infos, "Objet",
                dossier.getObject() != null ? dossier.getObject() : "—", fontBold, fontNormal);
        addRecepisseLine(infos, "Déposant", deposantLabel, fontBold, fontNormal);
        doc.add(infos);

        addRecepisseCodeSuivi(doc, dossier, fontBold, fontNormal);

        doc.add(new Paragraph(
                "Ce récépissé atteste de la réception de votre dénonciation ou plainte par "
                        + "l'ASCE-LC. Conservez le code de suivi ci-dessus : il vous permet de "
                        + "suivre l'état d'avancement de votre dossier sur le portail en ligne, "
                        + "sans qu'aucune information permettant de vous identifier ne soit requise.")
                .setFont(fontNormal).setFontSize(10)
                .setFontColor(new DeviceRgb(40, 40, 40))
                .setMarginTop(20));
    }

    private void addRecepisseLine(Table table, String label, String value,
                                  PdfFont fontBold, PdfFont fontNormal) {
        table.addCell(new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(0).setPaddingBottom(9)
                .add(new Paragraph(label).setFont(fontNormal).setFontSize(9.5f)
                        .setFontColor(TEXTE_GRIS)));
        table.addCell(new Cell().setBorder(Border.NO_BORDER).setPaddingBottom(9)
                .add(new Paragraph(value).setFont(fontNormal).setFontSize(11)
                        .setFontColor(new DeviceRgb(30, 30, 30))));
    }

    /** Encadré du code de suivi, avec un QR code qui ouvre directement la page de suivi du portail. */
    private void addRecepisseCodeSuivi(Document doc, DossierResponse dossier,
                                       PdfFont fontBold, PdfFont fontNormal) {
        String code = dossier.getAccessCode();
        if (code == null || code.isBlank()) {
            return;
        }

        Table box = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(VERT_CLAIR)
                .setBorder(Border.NO_BORDER);

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(16);
        left.add(new Paragraph("Code de suivi")
                .setFont(fontNormal).setFontSize(9.5f).setFontColor(TEXTE_GRIS)
                .setMarginBottom(4));
        left.add(new Paragraph(code)
                .setFont(fontBold).setFontSize(24).setFontColor(VERT_ASCE)
                .setCharacterSpacing(3).setMarginBottom(6));
        left.add(new Paragraph("À saisir sur le portail, ou à scanner avec votre téléphone.")
                .setFont(fontNormal).setFontSize(9).setFontColor(TEXTE_GRIS));
        box.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(12)
                .setTextAlignment(TextAlignment.RIGHT);
        try {
            String base = frontendUrl != null && !frontendUrl.isBlank()
                    ? frontendUrl.replaceAll("/+$", "") : "http://localhost:4200";
            // L'application utilise le routage par « # » : /#/portail/suivi?code=...
            String url = base + "/#/portail/suivi?code="
                    + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8);
            BarcodeQRCode qr = new BarcodeQRCode(url);
            right.add(new Image(qr.createFormXObject(ColorConstants.BLACK, doc.getPdfDocument()))
                    .setWidth(78).setHeight(78)
                    .setHorizontalAlignment(HorizontalAlignment.RIGHT));
        } catch (Exception e) {
            // Le QR code est un confort : le récépissé reste valable sans lui.
            log.warn("QR code du récépissé non généré : {}", e.getMessage());
        }
        box.addCell(right);
        doc.add(box);
    }

    private void addRecepisseFooter(Document doc, PdfFont fontBold, PdfFont fontNormal) {

        doc.add(new Paragraph(
                AsceLcInstitutionalInfo.ADDRESS + "\n"
                        + "Tél. : " + AsceLcInstitutionalInfo.PHONE
                        + "  |  " + AsceLcInstitutionalInfo.EMAIL_INFO
                        + "  |  " + AsceLcInstitutionalInfo.WEBSITE
                        + "  |  Numéro vert : " + AsceLcInstitutionalInfo.NUMERO_VERT)
                .setFont(fontNormal).setFontSize(8).setFontColor(TEXTE_GRIS)
                .setMultipliedLeading(1.4f)
                .setMarginTop(36));

        doc.add(new Paragraph(AsceLcInstitutionalInfo.SLOGAN)
                .setFont(fontBold).setFontSize(8).setFontColor(VERT_ASCE)
                .setMarginTop(4));
    }



    private void addHeader(Document doc, DossierResponse dossier,
                           PdfFont fontBold, PdfFont fontNormal) {
        addRecepisseHeader(doc, dossier, fontBold, fontNormal);

        String numero = dossier.getNumber() != null ? dossier.getNumber() : "En attente d'enregistrement";
        String ligne = "Dossier " + numero
                + "  |  " + getStatusLabel(dossier.getStatus().name())
                + (dossier.getAccessCode() != null ? "  |  Code de suivi : " + dossier.getAccessCode() : "");
        addTitreDocument(doc, "Fiche de dossier", ligne, fontBold, fontNormal);
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

        if (Boolean.TRUE.equals(dossier.getAnonymous())) {

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

            // Un lanceur d'alerte protégé (masqué par
            // DossierServiceImpl.maskSensitiveData) a prénom/nom vides mais un
            // displayName standard ("Lanceur d'alerte protégé...") — on
            // l'affiche plutôt que d'omettre silencieusement la ligne.
            String nomAffiche = !nom.isEmpty() ? nom : declarant.getDisplayName();

            if (nomAffiche != null && !nomAffiche.isBlank()) {
                addInfoCell(table, "Nom complet", nomAffiche, fontBold, fontNormal);
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
                                "Protection lanceur d'alerte demandée")
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
                    .setFontSize(11)
                    .setFontColor(new DeviceRgb(30, 30, 30))
                    .setMarginBottom(12));
        }

        if (dossier.getDescription() != null) {
            doc.add(sectionTitle("Description des faits", fontBold));
            doc.add(new Paragraph(dossier.getDescription())
                    .setFont(fontNormal)
                    .setFontSize(10)
                    .setFontColor(new DeviceRgb(40, 40, 40))
                    .setMultipliedLeading(1.3f)
                    .setMarginBottom(16));
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
                    .setBorder(Border.NO_BORDER)
                    .setBorderBottom(new SolidBorder(VERT_ASCE, 1))
                    .setPaddingLeft(0).setPaddingBottom(6)
                    .add(new Paragraph(h)
                            .setFont(fontBold)
                            .setFontSize(9)
                            .setFontColor(VERT_ASCE)));
        }


        for (StatusHistory h : history) {
            DeviceRgb bg = BLANC;

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

        doc.add(new Paragraph(
                "Document confidentiel — ASCE-LC Burkina Faso | " +
                        AsceLcInstitutionalInfo.ADDRESS + " | " +
                        "Tél: " + AsceLcInstitutionalInfo.PHONE + " | " +
                        "Numéro vert: " + AsceLcInstitutionalInfo.NUMERO_VERT)
                .setFont(fontNormal)
                .setFontSize(8)
                .setFontColor(TEXTE_GRIS)
                .setMarginTop(30)
                .setMarginBottom(4));

        String generated = "Document généré le " + FMT.format(java.time.Instant.now());
        if (dossier.getNumber() != null) {
            generated += " — Réf: " + dossier.getNumber();
        }

        doc.add(new Paragraph(generated)
                .setFont(fontNormal)
                .setFontSize(7)
                .setFontColor(TEXTE_GRIS));
    }



    private Paragraph sectionTitle(String title, PdfFont fontBold) {
        return new Paragraph(title)
                .setFont(fontBold)
                .setFontSize(12)
                .setFontColor(VERT_ASCE)
                .setMarginTop(18)
                .setMarginBottom(8);
    }

    private void addInfoCell(Table table, String label, String value,
                             PdfFont fontBold, PdfFont fontNormal) {
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(0).setPaddingRight(10).setPaddingTop(0).setPaddingBottom(10);

        cell.add(new Paragraph(label)
                .setFont(fontNormal)
                .setFontSize(8.5f)
                .setFontColor(TEXTE_GRIS)
                .setMarginBottom(1));

        cell.add(new Paragraph(value != null ? value : "—")
                .setFont(fontNormal)
                .setFontSize(10.5f)
                .setFontColor(new DeviceRgb(30, 30, 30)));

        table.addCell(cell);
    }

    private void addTableRow(Table table, DeviceRgb bg,
                             PdfFont fontNormal, String... values) {
        for (String value : values) {
            table.addCell(new Cell()
                    .setBackgroundColor(bg)
                    .setBorder(Border.NO_BORDER)
                    .setBorderBottom(new SolidBorder(new DeviceRgb(225, 225, 225), 0.5f))
                    .setPaddingLeft(0).setPaddingTop(6).setPaddingBottom(6)
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
            case "ORIENTEE_ADMINISTRATIF" -> "Orienté (autorité hiérarchique)";
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
            case "DENONCIATION"  -> "Dénonciation";
            case "PLAINTE"       -> "Plainte";
            case "SIGNALEMENT"   -> "Signalement";
            case "AUTO_SAISINE"  -> "Auto-saisine";
            default              -> type;
        };
    }

    private String getModeLabel(String mode) {
        return switch (mode) {
            case "IN_PERSON"    -> "Guichet BRPD";
            case "WEB_FORM"     -> "Formulaire Web";
            case "EMAIL"        -> "Email";
            case "PHONE"        -> "Téléphone";
            case "FAX"          -> "Fax";
            case "GREEN_NUMBER" -> "Numéro Vert";
            case "AUDIO_COUNTER"-> "Comptoir Audio";
            case "PAPER_FORM"   -> "Formulaire Papier";
            default             -> mode;
        };
    }
}