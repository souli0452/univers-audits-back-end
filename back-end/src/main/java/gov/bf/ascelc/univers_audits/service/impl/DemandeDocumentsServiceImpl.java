package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsAddressErrorRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.service.DemandeDocumentsService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandeDocumentsServiceImpl implements DemandeDocumentsService {

    private final DemandeDocumentsRepository demandeDocumentsRepository;
    private final InvestigationRepository    investigationRepository;
    private final ParametreDelaiService      parametreDelaiService;
    private final DossierDetailsMapper       mapper;
    private final AgentContextResolver       agentContextResolver;
    private final DossierAccessGuard         accessGuard;

    @Override
    @Transactional
    public DemandeDocumentsResponse create(UUID investigationId, DemandeDocumentsCreateRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        int deadlineDays = parametreDelaiService.resolveDelaiJours(delaiCodeFor(EscalationLevel.INITIAL));
        Instant sentAt = Instant.now();

        DemandeDocuments demande = DemandeDocuments.builder()
                .investigation(investigation)
                .recipientLabel(request.getRecipientLabel())
                .documentsRequested(request.getDocumentsRequested())
                .requestedBy(agentContextResolver.getCurrentAgent())
                .sentAt(sentAt)
                .deadline(sentAt.plusSeconds(deadlineDays * 24L * 3600))
                .build();

        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents créée — investigation: {}, id: {}", investigationId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DemandeDocumentsResponse markReceived(UUID id) {
        DemandeDocuments demande = getOrThrow(id);
        accessGuard.checkReadAccess(demande.getInvestigation().getDossier());

        demande.markReceived();
        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents reçue — id: {}", id);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DemandeDocumentsResponse escalate(UUID id) {
        DemandeDocuments demande = getOrThrow(id);
        accessGuard.checkReadAccess(demande.getInvestigation().getDossier());

        if (Boolean.TRUE.equals(demande.getReceived())) {
            throw new BusinessException("Cette demande a déjà été satisfaite, elle ne peut pas être escaladée");
        }
        if (!demande.isOverdue()) {
            throw new BusinessException("Cette demande n'est pas encore en retard, elle ne peut pas être escaladée");
        }
        EscalationLevel nextLevel = demande.nextEscalationLevel();
        if (nextLevel == null) {
            throw new BusinessException("Cette demande est déjà au niveau d'escalade maximal (saisine judiciaire)");
        }

        int deadlineDays = parametreDelaiService.resolveDelaiJours(delaiCodeFor(nextLevel));
        demande.escalate(nextLevel, deadlineDays);

        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents escaladée — id: {}, niveau: {}", id, nextLevel);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DemandeDocumentsResponse reportAddressError(
            UUID id, DemandeDocumentsAddressErrorRequest request) {
        DemandeDocuments demande = getOrThrow(id);
        accessGuard.checkReadAccess(demande.getInvestigation().getDossier());

        if (Boolean.TRUE.equals(demande.getReceived())) {
            throw new BusinessException(
                    "Cette demande a déjà été satisfaite, elle ne peut pas être "
                            + "corrigée pour adresse erronée");
        }

        int deadlineDays = parametreDelaiService.resolveDelaiJours(
                delaiCodeFor(demande.getEscalationLevel()));
        demande.resetForAddressError(request.getCorrectedRecipientLabel(), deadlineDays);

        DemandeDocuments saved = demandeDocumentsRepository.save(demande);
        log.info("Demande de documents corrigée pour adresse erronée — id: {}", id);
        return mapper.toResponse(saved);
    }

    @Override
    public List<DemandeDocumentsResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return demandeDocumentsRepository.findByInvestigationIdOrderBySentAtDesc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private String delaiCodeFor(EscalationLevel level) {
        return switch (level) {
            case INITIAL -> "DEMANDE_DOCUMENTS_INITIAL";
            case RELANCE -> "DEMANDE_DOCUMENTS_RELANCE";
            case SOMMATION -> "DEMANDE_DOCUMENTS_SOMMATION";
            case SAISINE_JUDICIAIRE -> "DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE";
        };
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private DemandeDocuments getOrThrow(UUID id) {
        return demandeDocumentsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demande de documents introuvable : " + id));
    }
}
