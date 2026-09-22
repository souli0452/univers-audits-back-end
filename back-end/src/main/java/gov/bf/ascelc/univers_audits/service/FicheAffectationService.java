package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DepartementRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FicheAffectationService {

    private final FicheAffectationRepository ficheAffectationRepository;
    private final DossierRepository dossierRepository;
    private final DepartementRepository departementRepository;
    private final AgentRepository agentRepository;
    private final NotificationRepository notificationRepository;
    private final PortalConfigService portalConfigService;
    private final KeycloakAdminService keycloakAdminService;
    private final SecurityUtils securityUtils;
    private final DossierAccessGuard accessGuard;

    @Transactional
    public FicheAffectation creer(UUID dossierId, FicheAffectationCreateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        if (ficheAffectationRepository.existsByDossierId(dossierId)) {
            throw new ConflictException(
                    "Une fiche d'affectation existe déjà pour ce dossier : " + dossierId);
        }

        Agent agentCge = getCurrentAgentOrThrow();

        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .decisionCge(request.getDecisionCge())
                .observationsCge(request.getObservationsCge())
                .agentCge(agentCge)
                .dateDecisionCge(Instant.now())
                .build();

        FicheAffectation saved = ficheAffectationRepository.save(fiche);
        log.info("Fiche d'affectation créée — dossier: {}", dossierId);
        return saved;
    }

    private Agent getCurrentAgentOrThrow() {
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
    }

    private FicheAffectation getFicheOrThrow(UUID dossierId) {
        return ficheAffectationRepository.findByDossierId(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune fiche d'affectation n'existe pour ce dossier : " + dossierId));
    }

    private Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }
}
