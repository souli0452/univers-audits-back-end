package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
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
    @Mock private AgentRepository      agentRepository;
    @Mock private DossierAccessGuard   accessGuard;
    @Mock private SecurityUtils        securityUtils;
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
    void upload_defaultsSourceAndModeObtentionWhenNotProvided() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
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
    void upload_usesProvidedSourceModeObtentionAndPersonneRemettante() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.jpg", "image/jpeg", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
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
                        && a.getCode().equals("ACC-T-00005")));
    }

    @Test
    void upload_setsUploadedByWhenAgentAuthenticated() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-agent-1"));
        when(agentRepository.findByKeycloakId("kc-agent-1")).thenReturn(Optional.of(agent));
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

        verify(attachmentRepository).save(argThat(a -> a.getUploadedBy() == agent));
    }

    @Test
    void generateUniqueAttachmentCode_retriesOnCollision() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
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
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
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
        SectionDossierTravail section = SectionDossierTravail.builder().build();
        section.setId(sectionId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(sectionDossierTravailRepository.getReferenceById(sectionId)).thenReturn(section);
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
    void reclasser_changeLaSectionDUnePieceDejaDeposee() {
        UUID attachmentId = UUID.randomUUID();
        UUID nouvelleSectionId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().build();
        attachment.setId(attachmentId);
        SectionDossierTravail nouvelleSection = SectionDossierTravail.builder().build();
        nouvelleSection.setId(nouvelleSectionId);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(sectionDossierTravailRepository.getReferenceById(nouvelleSectionId))
                .thenReturn(nouvelleSection);

        service.reclasser(attachmentId, nouvelleSectionId.toString());

        assertThat(attachment.getSection()).isEqualTo(nouvelleSection);
        verify(attachmentRepository).save(attachment);
    }

    @Test
    void reclasser_avecSectionIdNulDeclasseLaPiece() {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().build();
        attachment.setId(attachmentId);
        SectionDossierTravail ancienneSection = SectionDossierTravail.builder().build();
        ancienneSection.setId(UUID.randomUUID());
        attachment.setSection(ancienneSection);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.reclasser(attachmentId, null);

        assertThat(attachment.getSection()).isNull();
        verify(attachmentRepository).save(attachment);
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
}
