package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private DossierRepository    dossierRepository;
    @Mock private DossierAccessGuard   accessGuard;

    @InjectMocks
    private AttachmentStorageService service;

    @Test
    void upload_checksAttachmentUploadAccessBeforeWritingAnything() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder()
                .id(dossierId)
                .status(DossierStatus.EN_INVESTIGATION)
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkAttachmentUploadAccess(dossier, null);

        assertThatThrownBy(() -> service.upload(dossierId.toString(), List.of(), null))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }
}
