package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.CorrectionPvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.CorrectionPvAudition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.CorrectionPvAuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.service.PvAuditionService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PvAuditionServiceImpl implements PvAuditionService {

    private final PVAuditionRepository           pvAuditionRepository;
    private final CorrectionPvAuditionRepository correctionPvAuditionRepository;
    private final AuditionRepository             auditionRepository;
    private final DossierDetailsMapper           mapper;
    private final AgentContextResolver           agentContextResolver;
    private final DossierAccessGuard             accessGuard;

    @Override
    @Transactional
    public PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request) {
        Audition audition = auditionRepository.findById(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audition introuvable : " + auditionId));
        Dossier dossier = audition.getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);
        checkConfidentialAccess(dossier);

        if (audition.getStatus() != AuditionStatus.CONDUCTED) {
            throw new BusinessException(
                    "Un procès-verbal ne peut être rédigé que pour une audition tenue (statut actuel : "
                            + audition.getStatus() + ")");
        }
        if (pvAuditionRepository.findByAuditionId(auditionId).isPresent()) {
            throw new BusinessException(
                    "Un procès-verbal existe déjà pour cette audition");
        }

        PVAudition pv = PVAudition.builder()
                .audition(audition)
                .content(request.getContent())
                .draftedBy(agentContextResolver.getCurrentAgent())
                .build();

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition créé — audition: {}, id: {}", auditionId, saved.getId());
        return toResponseWithCorrections(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse markReadBack(UUID auditionId) {
        PVAudition pv = getPvOrThrow(auditionId);
        Dossier dossier = pv.getAudition().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);
        checkConfidentialAccess(dossier);

        if (pv.isFinalized()) {
            throw new BusinessException("Ce procès-verbal est déjà finalisé");
        }
        if (pv.getReadBackAt() != null) {
            throw new BusinessException(
                    "La relecture a déjà été enregistrée pour ce procès-verbal");
        }
        pv.setReadBackAt(Instant.now());
        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition relu à la personne auditionnée — audition: {}", auditionId);
        return toResponseWithCorrections(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request) {
        PVAudition pv = getPvOrThrow(auditionId);
        Dossier dossier = pv.getAudition().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);
        checkConfidentialAccess(dossier);

        if (Boolean.TRUE.equals(request.getIntervieweeSigned())
                && Boolean.TRUE.equals(request.getIntervieweeSignatureRefused())) {
            throw new BusinessException(
                    "Un PV ne peut pas être à la fois signé et refusé par la personne auditionnée");
        }
        if (pv.isFinalized()) {
            throw new BusinessException("Ce procès-verbal est déjà finalisé");
        }
        if (pv.getReadBackAt() == null) {
            throw new BusinessException(
                    "Le procès-verbal doit être relu à la personne auditionnée avant signature");
        }

        pv.finalizeSignatures(
                Boolean.TRUE.equals(request.getIntervieweeSigned()),
                Boolean.TRUE.equals(request.getIntervieweeSignatureRefused()));

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition finalisé — audition: {}", auditionId);
        return toResponseWithCorrections(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse correct(UUID auditionId, PvAuditionCorrectionRequest request) {
        PVAudition pv = getPvOrThrow(auditionId);
        Dossier dossier = pv.getAudition().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);
        checkConfidentialAccess(dossier);

        if (!pv.isFinalized()) {
            throw new BusinessException(
                    "Seul un procès-verbal finalisé peut faire l'objet d'une correction");
        }

        CorrectionPvAudition correction = CorrectionPvAudition.builder()
                .pvAudition(pv)
                .versionNumber(pv.getPvVersion())
                .content(pv.getContent())
                .correctedAt(Instant.now())
                .correctedBy(agentContextResolver.getCurrentAgent())
                .motifCorrection(request.getMotifCorrection())
                .build();
        correctionPvAuditionRepository.save(correction);

        pv.setContent(request.getContent());
        pv.setPvVersion(pv.getPvVersion() + 1);
        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition corrigé — audition: {}, nouvelle version: {}",
                auditionId, saved.getPvVersion());
        return toResponseWithCorrections(saved);
    }

    @Override
    public PvAuditionResponse findByAuditionId(UUID auditionId) {
        PVAudition pv = getPvOrThrow(auditionId);
        Dossier dossier = pv.getAudition().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);
        checkConfidentialAccess(dossier);

        return toResponseWithCorrections(pv);
    }

    private void checkConfidentialAccess(Dossier dossier) {
        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — le procès-verbal d'un dossier confidentiel n'est visible que par les rôles habilités");
        }
    }

    private PvAuditionResponse toResponseWithCorrections(PVAudition pv) {
        PvAuditionResponse response = mapper.toResponse(pv);
        response.setCorrections(
                correctionPvAuditionRepository.findByPvAuditionIdOrderByVersionNumberAsc(pv.getId())
                        .stream()
                        .map(this::toCorrectionResponse)
                        .toList());
        return response;
    }

    private CorrectionPvAuditionResponse toCorrectionResponse(CorrectionPvAudition correction) {
        return CorrectionPvAuditionResponse.builder()
                .id(correction.getId())
                .versionNumber(correction.getVersionNumber())
                .content(correction.getContent())
                .correctedAt(correction.getCorrectedAt())
                .correctedById(correction.getCorrectedBy().getId())
                .correctedByName(correction.getCorrectedBy().getNomComplet())
                .motifCorrection(correction.getMotifCorrection())
                .build();
    }

    private PVAudition getPvOrThrow(UUID auditionId) {
        return pvAuditionRepository.findByAuditionId(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procès-verbal introuvable pour l'audition : " + auditionId));
    }
}
