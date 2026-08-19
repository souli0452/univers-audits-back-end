package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ConstitutionPartieCivileRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ConstitutionPartieCivileResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ConstitutionPartieCivileRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
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
public class ConstitutionPartieCivileService {

    private final ConstitutionPartieCivileRepository constitutionPartieCivileRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public ConstitutionPartieCivileResponse creer(UUID investigationId, ConstitutionPartieCivileRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "La constitution de partie civile n'est possible qu'après la décision finale du CGE.");
        }
        if (constitutionPartieCivileRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "L'ASCE-LC s'est déjà constituée partie civile pour ce dossier.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        ConstitutionPartieCivile constitution = ConstitutionPartieCivile.builder()
                .investigation(investigation)
                .constitueAt(Instant.now())
                .montantReclame(request.getMontantReclame())
                .justification(request.getJustification())
                .constitueePar(agent)
                .submittedAt(Instant.now())
                .build();

        ConstitutionPartieCivile saved = constitutionPartieCivileRepository.save(constitution);
        log.info("Constitution de partie civile enregistrée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    public ConstitutionPartieCivileResponse getOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucune constitution de partie civile enregistrée pour cette investigation : " + investigationId);
        }

        ConstitutionPartieCivile constitution = constitutionPartieCivileRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune constitution de partie civile enregistrée pour cette investigation : " + investigationId));
        return toResponse(constitution);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private ConstitutionPartieCivileResponse toResponse(ConstitutionPartieCivile c) {
        return ConstitutionPartieCivileResponse.builder()
                .id(c.getId())
                .investigationId(c.getInvestigation().getId())
                .constitueAt(c.getConstitueAt())
                .montantReclame(c.getMontantReclame())
                .justification(c.getJustification())
                .constitueeParNom(c.getConstitueePar().getNomComplet())
                .submittedAt(c.getSubmittedAt())
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
