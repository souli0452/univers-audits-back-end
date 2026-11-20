package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private DossierRepository    dossierRepository;
    @Mock private DossierAccessGuard   accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private AccessCodeGenerator  accessCodeGenerator;
    @Mock private SectionDossierTravailRepository sectionDossierTravailRepository;

    @InjectMocks
    private AttachmentStorageService service;

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setUp() {
        // @Value n'est traité que dans un contexte Spring ; MockitoExtension ne
        // le résout pas, donc on injecte manuellement un répertoire temporaire
        // pour que Paths.get(uploadDir, ...) ne reçoive jamais null.
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private Dossier buildDossier(UUID id) {
        return Dossier.builder().id(id).status(DossierStatus.EN_INVESTIGATION).build();
    }

    @Test
    void upload_checksAttachmentUploadAccessBeforeWritingAnything() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkAttachmentUploadAccess(dossier, null);

        assertThatThrownBy(() -> service.upload(
                dossierId.toString(), List.of(), null, null, null, null, null))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_rejetteSiPersonneRemettanteDepasse255Caracteres() {
        UUID dossierId = UUID.randomUUID();
        String tropLong = "x".repeat(256);

        assertThatThrownBy(() -> service.upload(
                dossierId.toString(), List.of(), null, null, null, tropLong, null))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
        verify(dossierRepository, never()).findById(any());
    }

    @Test
    void upload_defaultsSourceAndModeObtentionWhenNotProvided() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null, null);

        verify(attachmentRepository).save(argThat(a ->
                a.getSource() == AttachmentSource.INITIAL_SUBMISSION
                        && a.getModeObtention() == ModeObtention.VOLONTAIRE
                        && a.getUploadedBy() == null));
    }

    @Test
    void upload_usesProvidedSourceModeObtentionAndPersonneRemettanteWhenAgentAuthenticated() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.jpg", "image/jpeg", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(agent);
        when(attachmentRepository.count()).thenReturn(4L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-T-00005");
        when(attachmentRepository.existsByCode("ACC-T-00005")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null,
                AttachmentSource.FIELD_INVESTIGATION, ModeObtention.REQUISITION, "Jean Kaboré",
                null);

        verify(attachmentRepository).save(argThat(a ->
                a.getSource() == AttachmentSource.FIELD_INVESTIGATION
                        && a.getModeObtention() == ModeObtention.REQUISITION
                        && a.getPersonneRemettante().equals("Jean Kaboré")
                        && a.getCode().equals("ACC-T-00005")
                        && a.getUploadedBy() == agent));
    }

    @Test
    void upload_ignoresProvidedSourceModeObtentionAndPersonneRemettanteWhenAnonymous() throws Exception {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        // Au dépôt public, une image doit être une vraie image : ses métadonnées sont retirées avant stockage.
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.jpg", "image/jpeg", photoAvecExif());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        // Un citoyen anonyme (accessCode, sans authentification) ne doit pas
        // pouvoir s'attribuer une provenance/mode d'obtention d'agent — vérifie
        // la correction du finding "usurpation" de la revue finale.
        service.upload(dossierId.toString(), List.of(file), null,
                AttachmentSource.FIELD_INVESTIGATION, ModeObtention.REQUISITION, "Jean Kaboré",
                null);

        verify(attachmentRepository).save(argThat(a ->
                a.getSource() == AttachmentSource.INITIAL_SUBMISSION
                        && a.getModeObtention() == ModeObtention.VOLONTAIRE
                        && a.getPersonneRemettante() == null
                        && a.getUploadedBy() == null));
    }

    @Test
    void generateUniqueAttachmentCode_retriesOnCollision() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), eq(1L))).thenReturn("ACC-S-00001");
        when(accessCodeGenerator.generateAttachmentCode(any(), eq(2L))).thenReturn("ACC-S-00002");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(true);
        when(attachmentRepository.existsByCode("ACC-S-00002")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null, null);

        verify(attachmentRepository).save(argThat(a -> a.getCode().equals("ACC-S-00002")));
    }

    @Test
    void upload_sansSectionIdLaisseSectionNulle() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null, null);

        verify(attachmentRepository).save(argThat(a -> a.getSection() == null));
    }

    @Test
    void upload_avecSectionIdRattacheLaPieceALaBonneSection() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        UUID sectionId = UUID.randomUUID();
        SectionDossierTravail section = SectionDossierTravail.builder().dossier(dossier).build();
        section.setId(sectionId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(sectionDossierTravailRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null,
                sectionId.toString());

        verify(attachmentRepository).save(argThat(a -> a.getSection() == section));
    }

    @Test
    void upload_rejetteSiLaSectionAppartientAUnAutreDossier() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        Dossier autreDossier = buildDossier(UUID.randomUUID());
        UUID sectionId = UUID.randomUUID();
        SectionDossierTravail section = SectionDossierTravail.builder().dossier(autreDossier).build();
        section.setId(sectionId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionDossierTravailRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.upload(dossierId.toString(), List.of(file), null,
                null, null, null, sectionId.toString()))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_rejetteSiLaSectionEstIntrouvable() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        UUID sectionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionDossierTravailRepository.findById(sectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(dossierId.toString(), List.of(file), null,
                null, null, null, sectionId.toString()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void reclasser_changeLaSectionDUnePieceDejaDeposee() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        UUID attachmentId = UUID.randomUUID();
        UUID nouvelleSectionId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().dossier(dossier).build();
        attachment.setId(attachmentId);
        SectionDossierTravail nouvelleSection = SectionDossierTravail.builder().dossier(dossier).build();
        nouvelleSection.setId(nouvelleSectionId);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(sectionDossierTravailRepository.findById(nouvelleSectionId))
                .thenReturn(Optional.of(nouvelleSection));

        service.reclasser(attachmentId, nouvelleSectionId.toString());

        assertThat(attachment.getSection()).isEqualTo(nouvelleSection);
        verify(attachmentRepository).save(attachment);
    }

    @Test
    void reclasser_avecSectionIdNulDeclasseLaPiece() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().dossier(dossier).build();
        attachment.setId(attachmentId);
        SectionDossierTravail ancienneSection = SectionDossierTravail.builder().dossier(dossier).build();
        ancienneSection.setId(UUID.randomUUID());
        attachment.setSection(ancienneSection);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);

        service.reclasser(attachmentId, null);

        assertThat(attachment.getSection()).isNull();
        verify(attachmentRepository).save(attachment);
    }

    @Test
    void reclasser_rejetteSiAgentNonHabiliteSurLeDossier() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().dossier(dossier).build();
        attachment.setId(attachmentId);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.reclasser(attachmentId, null))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void reclasser_rejetteSiDossierConfidentielEtAgentNePeutPasVoirLeConfidentiel() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_INVESTIGATION).isConfidential(true).build();
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().dossier(dossier).build();
        attachment.setId(attachmentId);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.reclasser(attachmentId, null))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void reclasser_rejetteSiLaSectionAppartientAUnAutreDossier() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        Dossier autreDossier = buildDossier(UUID.randomUUID());
        UUID attachmentId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().dossier(dossier).build();
        attachment.setId(attachmentId);
        SectionDossierTravail section = SectionDossierTravail.builder().dossier(autreDossier).build();
        section.setId(sectionId);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(sectionDossierTravailRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.reclasser(attachmentId, sectionId.toString()))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void listByDossier_populatesEnrichedFields() {
        UUID dossierId = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .id(UUID.randomUUID())
                .originalName("preuve.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .status(gov.bf.ascelc.univers_audits.enums.AttachmentStatus.PENDING_VALIDATION)
                .description("Facture suspecte")
                .source(AttachmentSource.FIELD_INVESTIGATION)
                .modeObtention(ModeObtention.REQUISITION)
                .code("ACC-T-00007")
                .build();
        when(attachmentRepository.findByDossierId(dossierId)).thenReturn(List.of(att));

        List<AttachmentStorageService.AttachmentSummary> summaries = service.listByDossier(dossierId);

        assertThat(summaries).hasSize(1);
        AttachmentStorageService.AttachmentSummary summary = summaries.get(0);
        assertThat(summary.description()).isEqualTo("Facture suspecte");
        assertThat(summary.source()).isEqualTo("FIELD_INVESTIGATION");
        assertThat(summary.modeObtention()).isEqualTo("REQUISITION");
        assertThat(summary.code()).isEqualTo("ACC-T-00007");
    }

    // ── pièces jointes fiables : refus nommés, formats de téléphone, métadonnées retirées ──────────

    private static final String SECRET = "SECRET-AUTEUR-GPS";

    /** Vraie photo JPEG dont un bloc EXIF contient un texte reconnaissable (modèle d'appareil, position…). */
    private static byte[] photoAvecExif() throws java.io.IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream jpeg = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", jpeg);
        byte[] base = jpeg.toByteArray();
        byte[] charge = ("Exif\0\0" + SECRET).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.io.ByteArrayOutputStream s = new java.io.ByteArrayOutputStream();
        s.write(base, 0, 2);
        s.write(0xFF); s.write(0xE1); s.write((charge.length + 2) >> 8); s.write((charge.length + 2) & 0xFF);
        s.writeBytes(charge);
        s.write(base, 2, base.length - 2);
        return s.toByteArray();
    }

    private static boolean contient(byte[] octets, String texte) {
        return new String(octets, java.nio.charset.StandardCharsets.ISO_8859_1).contains(texte);
    }

    private void deposantAnonyme(UUID dossierId) {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(buildDossier(dossierId)));
        lenient().when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        lenient().when(attachmentRepository.findByDossierId(dossierId)).thenReturn(List.of());
        prepareEnregistrement();
    }

    private void agentAuthentifie(UUID dossierId) {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(buildDossier(dossierId)));
        lenient().when(agentContextResolver.getCurrentAgentOrNull())
                .thenReturn(Agent.builder().id(UUID.randomUUID()).build());
        prepareEnregistrement();
    }

    private void prepareEnregistrement() {
        lenient().when(attachmentRepository.count()).thenReturn(0L);
        lenient().when(accessCodeGenerator.generateAttachmentCode(any(), anyLong())).thenReturn("ACC-S-00001");
        lenient().when(attachmentRepository.existsByCode(any())).thenReturn(false);
        lenient().when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
    }

    private List<Attachment> enregistrees() {
        org.mockito.ArgumentCaptor<Attachment> c = org.mockito.ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository, atLeastOnce()).save(c.capture());
        return c.getAllValues();
    }

    private long nombreDeFichiersEcrits() throws java.io.IOException {
        try (var flux = java.nio.file.Files.walk(uploadDir)) {
            return flux.filter(java.nio.file.Files::isRegularFile).count();
        }
    }

    @Test
    void upload_public_retireLesInformationsCacheesDUnePhoto() throws Exception {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);
        byte[] photo = photoAvecExif();
        assertThat(contient(photo, SECRET)).as("la photo de départ contient bien le secret").isTrue();

        service.upload(dossierId.toString(), List.of(new MockMultipartFile("files", "photo.jpg", "image/jpeg", photo)),
                null, null, null, null, null);

        Attachment pj = enregistrees().get(0);
        byte[] stocke = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(pj.getFilePath()));
        assertThat(contient(stocke, SECRET)).isFalse();
        assertThat(pj.getFileSizeBytes()).as("la taille enregistrée est celle du fichier stocké").isEqualTo(stocke.length);
        assertThat(pj.getHashSha256()).as("l'empreinte est celle du fichier stocké").isEqualTo(sha256(stocke));
    }

    @Test
    void upload_agentAuthentifie_gardeLesMetadonneesDeLaPiece() throws Exception {
        UUID dossierId = UUID.randomUUID();
        agentAuthentifie(dossierId);
        byte[] photo = photoAvecExif();

        service.upload(dossierId.toString(), List.of(new MockMultipartFile("files", "constat.jpg", "image/jpeg", photo)),
                null, AttachmentSource.FIELD_INVESTIGATION, ModeObtention.REQUISITION, null, null);

        byte[] stocke = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(enregistrees().get(0).getFilePath()));
        assertThat(stocke).as("la date et le lieu d'un constat peuvent faire partie de la preuve").isEqualTo(photo);
    }

    private static String sha256(byte[] octets) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(octets)) { sb.append(String.format("%02x", b)); }
        return sb.toString();
    }

    @Test
    void upload_refuseUnFichierDeTypeNonAccepte_enLeNommant_aulieuDeLePerdreEnSilence() throws Exception {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("virus.exe", "application/x-msdownload")), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("virus.exe");

        verify(attachmentRepository, never()).save(any());
        assertThat(nombreDeFichiersEcrits()).isZero();
    }

    @Test
    void upload_unSeulFichierRefuse_annuleTout_etNEcritRien() throws Exception {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("acte.pdf", "application/pdf"), fichier("virus.exe", "application/x-msdownload")),
                null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("virus.exe")
                .hasMessageNotContaining("acte.pdf");

        verify(attachmentRepository, never()).save(any());
        assertThat(nombreDeFichiersEcrits()).as("même le fichier valide n'est pas laissé orphelin").isZero();
    }

    @Test
    void upload_public_refuseUneImageIllisible_enLaNommant_sansRienEcrire() throws Exception {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("acte.pdf", "application/pdf"),
                        new MockMultipartFile("files", "photo.jpg", "image/jpeg", "pas une image".getBytes())),
                null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("photo.jpg");

        verify(attachmentRepository, never()).save(any());
        assertThat(nombreDeFichiersEcrits()).isZero();
    }

    @Test
    void upload_public_refuseUneImageDontOnNePeutPasRetirerLesMetadonnees() throws Exception {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("anim.gif", "image/gif")), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("anim.gif");
    }

    @Test
    void upload_agentAuthentifie_accepteUneImageGif() {
        UUID dossierId = UUID.randomUUID();
        agentAuthentifie(dossierId);

        service.upload(dossierId.toString(), List.of(fichier("anim.gif", "image/gif")), null, null, null, null, null);

        verify(attachmentRepository).save(any(Attachment.class));
    }

    @Test
    void upload_accepteLaNoteVocaleDUnTelephone_m4a() {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        service.upload(dossierId.toString(), List.of(fichier("note.m4a", "audio/x-m4a")), null, null, null, null, null);

        Attachment pj = enregistrees().get(0);
        assertThat(pj.getMimeType()).isEqualTo("audio/mp4");
        assertThat(pj.getType()).isEqualTo(gov.bf.ascelc.univers_audits.enums.AttachmentType.AUDIO_EVIDENCE);
    }

    @Test
    void upload_accepteLEnregistrementDuNavigateurAvecSesParametres() {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        service.upload(dossierId.toString(), List.of(fichier("t.webm", "audio/webm;codecs=opus")), null, null, null, null, null);

        assertThat(enregistrees().get(0).getMimeType()).isEqualTo("audio/webm");
    }

    @Test
    void upload_devineLeTypeQuandLeNavigateurNEnDonneAucun_opusDeWhatsApp() {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        service.upload(dossierId.toString(), List.of(fichier("note.opus", "application/octet-stream")), null, null, null, null, null);

        Attachment pj = enregistrees().get(0);
        assertThat(pj.getMimeType()).isEqualTo("audio/ogg");
        assertThat(pj.getType()).isEqualTo(gov.bf.ascelc.univers_audits.enums.AttachmentType.AUDIO_EVIDENCE);
    }

    @Test
    void upload_neSeFieJamaisAuxSeulesExtensions_unFichierSansTypeNiExtensionConnueEstRefuse() {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("script.sh", "application/octet-stream")), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("script.sh");
    }

    @Test
    void upload_uneErreurDEcriture_nelaisseAucunFichierOrphelin_etEstSignalee() throws Exception {
        UUID dossierId = UUID.randomUUID();
        deposantAnonyme(dossierId);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> { Attachment a = inv.getArgument(0); a.setId(UUID.randomUUID()); return a; })
                .thenThrow(new IllegalStateException("base indisponible"));

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("a.pdf", "application/pdf"), fichier("b.pdf", "application/pdf")),
                null, null, null, null, null))
                .isInstanceOf(RuntimeException.class);

        assertThat(nombreDeFichiersEcrits()).as("le premier fichier écrit est retiré quand le second échoue").isZero();
    }

    private static MockMultipartFile fichier(String nom, String mime) {
        return new MockMultipartFile("files", nom, mime, "x".getBytes());
    }

    private static Attachment existante(gov.bf.ascelc.univers_audits.enums.AttachmentType type, AttachmentSource source) {
        return Attachment.builder().type(type).source(source).build();
    }

    @Test
    void upload_public_refuseUnSixiemeDocumentMaisAccepteLeTemoignageAudio() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(buildDossier(dossierId)));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.findByDossierId(dossierId)).thenReturn(List.of(
                existante(gov.bf.ascelc.univers_audits.enums.AttachmentType.PHOTO, AttachmentSource.INITIAL_SUBMISSION),
                existante(gov.bf.ascelc.univers_audits.enums.AttachmentType.PHOTO, AttachmentSource.INITIAL_SUBMISSION),
                existante(gov.bf.ascelc.univers_audits.enums.AttachmentType.PHOTO, AttachmentSource.INITIAL_SUBMISSION),
                existante(gov.bf.ascelc.univers_audits.enums.AttachmentType.PHOTO, AttachmentSource.INITIAL_SUBMISSION),
                existante(gov.bf.ascelc.univers_audits.enums.AttachmentType.PHOTO, AttachmentSource.INITIAL_SUBMISSION)));

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("6.pdf", "application/pdf")), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Maximum 5 pièces jointes");

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_public_refuseUnDeuxiemeTemoignageAudio() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(buildDossier(dossierId)));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(null);
        when(attachmentRepository.findByDossierId(dossierId)).thenReturn(List.of(
                existante(gov.bf.ascelc.univers_audits.enums.AttachmentType.AUDIO_EVIDENCE, AttachmentSource.INITIAL_SUBMISSION)));

        assertThatThrownBy(() -> service.upload(dossierId.toString(),
                List.of(fichier("t.webm", "audio/webm")), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Un seul témoignage audio");
    }

    @Test
    void upload_agentAuthentifie_neSubitPasLeQuotaDuDeposant() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(buildDossier(dossierId)));
        when(agentContextResolver.getCurrentAgentOrNull()).thenReturn(Agent.builder().id(UUID.randomUUID()).build());

        service.upload(dossierId.toString(), List.of(), null, null, null, null, null);

        verify(attachmentRepository, never()).findByDossierId(any());
    }
}
