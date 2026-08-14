package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventairePiecesService {

    private final InvestigationRepository investigationRepository;
    private final AttachmentRepository attachmentRepository;
    private final DossierAccessGuard accessGuard;

    public List<InventairePieceItemResponse> getInventaire(UUID investigationId) {
        Investigation investigation = investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return attachmentRepository.findByInvestigationId(investigationId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    private InventairePieceItemResponse toItemResponse(Attachment attachment) {
        return InventairePieceItemResponse.builder()
                .attachmentId(attachment.getId())
                .code(attachment.getCode())
                .description(attachment.getDescription())
                .source(attachment.getSource())
                .uploadedAt(attachment.getUploadedAt())
                .modeObtention(attachment.getModeObtention())
                .build();
    }
}
