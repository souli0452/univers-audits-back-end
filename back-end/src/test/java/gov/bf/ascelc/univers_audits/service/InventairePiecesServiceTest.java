package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventairePiecesServiceTest {

    @Mock
    private InvestigationRepository investigationRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private DossierAccessGuard accessGuard;

    @InjectMocks
    private InventairePiecesService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder().id(investigationId).dossier(dossier).build();
    }

    @Test
    void getInventaire_mappeLesChampsDesPiecesJointes() {
        LocalDateTime uploadedAt1 = LocalDateTime.of(2026, 1, 10, 9, 0);
        LocalDateTime uploadedAt2 = LocalDateTime.of(2026, 1, 12, 14, 30);
        Attachment piece1 = Attachment.builder()
                .id(UUID.randomUUID())
                .code("ACC-S-00001")
                .description("Relevé bancaire")
                .source(AttachmentSource.INITIAL_SUBMISSION)
                .uploadedAt(uploadedAt1)
                .modeObtention(ModeObtention.VOLONTAIRE)
                .status(AttachmentStatus.VALIDATED)
                .build();
        Attachment piece2 = Attachment.builder()
                .id(UUID.randomUUID())
                .code("ACC-T-00002")
                .description("Facture saisie sur site")
                .source(AttachmentSource.FIELD_INVESTIGATION)
                .uploadedAt(uploadedAt2)
                .modeObtention(ModeObtention.REQUISITION)
                .status(AttachmentStatus.REJECTED)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(attachmentRepository.findByDossierId(dossier.getId()))
                .thenReturn(List.of(piece1, piece2));

        List<InventairePieceItemResponse> result = service.getInventaire(investigationId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("ACC-S-00001");
        assertThat(result.get(0).getDescription()).isEqualTo("Relevé bancaire");
        assertThat(result.get(0).getSource()).isEqualTo(AttachmentSource.INITIAL_SUBMISSION);
        assertThat(result.get(0).getUploadedAt()).isEqualTo(uploadedAt1);
        assertThat(result.get(0).getModeObtention()).isEqualTo(ModeObtention.VOLONTAIRE);
        assertThat(result.get(0).getStatus()).isEqualTo(AttachmentStatus.VALIDATED);
        assertThat(result.get(1).getCode()).isEqualTo("ACC-T-00002");
        assertThat(result.get(1).getModeObtention()).isEqualTo(ModeObtention.REQUISITION);
        assertThat(result.get(1).getStatus()).isEqualTo(AttachmentStatus.REJECTED);
    }

    @Test
    void getInventaire_renvoieListeVideSiAucunePieceJointe() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(attachmentRepository.findByDossierId(dossier.getId())).thenReturn(List.of());

        assertThat(service.getInventaire(investigationId)).isEmpty();
    }

    @Test
    void getInventaire_renvoieListeVideSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<InventairePieceItemResponse> result = service.getInventaire(investigationId);

        assertThat(result).isEmpty();
        verify(attachmentRepository, never()).findByDossierId(any());
    }

    @Test
    void getInventaire_leveSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getInventaire(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(attachmentRepository, never()).findByDossierId(any());
    }
}
