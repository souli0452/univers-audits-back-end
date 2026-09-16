package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.LeconAPartagerRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeconAPartagerService {

    private final LeconAPartagerRepository leconAPartagerRepository;
    private final FicheRetexRepository ficheRetexRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public LeconAPartagerResponse publier(UUID investigationId, PublierLeconRequest request) {
        Investigation investigation = investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        FicheRetex fiche = ficheRetexRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucune fiche RETEX n'a été rédigée pour cette investigation, "
                                + "impossible de publier une leçon."));

        if (leconAPartagerRepository.existsByFicheRetexId(fiche.getId())) {
            throw new BusinessException(
                    "Une leçon a déjà été publiée pour cette fiche RETEX.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();

        LeconAPartager lecon = LeconAPartager.builder()
                .ficheRetex(fiche)
                .titre(request.getTitre())
                .resume(request.getResume())
                .publieePar(agent)
                .build();

        LeconAPartager saved = leconAPartagerRepository.save(lecon);
        log.info("Leçon à partager publiée — fiche RETEX: {}", fiche.getId());
        return toResponse(saved, investigationId);
    }

    public Page<LeconAPartagerResponse> lister(Pageable pageable) {
        return leconAPartagerRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(lecon -> toResponse(lecon, lecon.getFicheRetex().getInvestigation().getId()));
    }

    private LeconAPartagerResponse toResponse(LeconAPartager lecon, UUID investigationId) {
        return LeconAPartagerResponse.builder()
                .id(lecon.getId())
                .titre(lecon.getTitre())
                .resume(lecon.getResume())
                .publieeParNom(lecon.getPublieePar().getNomComplet())
                .investigationId(investigationId)
                .createdAt(lecon.getCreatedAt())
                .build();
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }
}
