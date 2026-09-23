package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Departement;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> DEPARTEMENTS_ELIGIBLES = Set.of("DEI", "DAC");

    @Transactional
    public FicheAffectation creer(UUID dossierId, FicheAffectationCreateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        if (dossier.getNumber() == null) {
            throw new BusinessException(
                    "Le dossier doit être réceptionné (numéro attribué) avant de créer une fiche d'affectation.");
        }

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

    @Transactional
    public FicheAffectation affecter(UUID dossierId, FicheAffectationAffectationRequest request) {
        FicheAffectation fiche = getFicheOrThrow(dossierId);
        accessGuard.checkReadAccess(fiche.getDossier());

        List<Agent> destinataires = resolveDestinataires(request);

        fiche.setTypeDesignation(request.getTypeDesignation());
        fiche.setDepartementDesigne(
                request.getTypeDesignation() == TypeDesignation.DEPARTEMENT
                        ? getDepartementEligibleOrThrow(request.getDepartementDesigneId())
                        : null);
        fiche.setAgentDesigne(
                request.getTypeDesignation() == TypeDesignation.AGENT_CJ
                        ? getConseillerJuridiqueOrThrow(request.getAgentDesigneId())
                        : null);
        fiche.setObservationsCgea(request.getObservationsCgea());
        fiche.setAgentCgea(getCurrentAgentOrThrow());
        fiche.setDateImputation(Instant.now());

        FicheAffectation saved = ficheAffectationRepository.save(fiche);
        notifierDestinataires(saved, destinataires);
        log.info("Fiche d'affectation renseignée (section CGEA) — dossier: {}, type: {}",
                dossierId, request.getTypeDesignation());
        return saved;
    }

    @Transactional
    public FicheAffectation suivre(UUID dossierId, FicheAffectationSuiviRequest request) {
        FicheAffectation fiche = getFicheOrThrow(dossierId);
        checkSuiviAccess(fiche);

        if (request.getEtatAvancement() == EtatAvancementAffectation.AUTRE
                && (request.getEtatAvancementPrecision() == null
                    || request.getEtatAvancementPrecision().isBlank())) {
            throw new BusinessException(
                    "Une précision est requise quand l'état d'avancement est \"Autre\".");
        }

        fiche.setDateRetour(Instant.now());
        fiche.setEtatAvancement(request.getEtatAvancement());
        fiche.setEtatAvancementPrecision(request.getEtatAvancementPrecision());
        fiche.setCommentairesSuivi(request.getCommentairesSuivi());
        fiche.setAgentSuivi(getCurrentAgentOrThrow());

        FicheAffectation saved = ficheAffectationRepository.save(fiche);
        log.info("Fiche d'affectation — suivi renseigné — dossier: {}, état: {}",
                dossierId, request.getEtatAvancement());
        return saved;
    }

    public FicheAffectation getOrThrow(UUID dossierId) {
        FicheAffectation fiche = getFicheOrThrow(dossierId);
        checkSuiviAccess(fiche);
        return fiche;
    }

    private void checkSuiviAccess(FicheAffectation fiche) {
        if (accessGuard.canSeeConfidential()) {
            return; // CGE/CGEA/ADMIN_DDIC : accès toujours autorisé
        }
        if (fiche.getTypeDesignation() == null) {
            throw new BusinessException(
                    "Accès refusé — la section d'affectation n'a pas encore été renseignée par le CGEA");
        }
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        Agent agent = agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));

        boolean autorise = switch (fiche.getTypeDesignation()) {
            case DEPARTEMENT -> fiche.getDepartementDesigne() != null
                    && agent.getDepartement() != null
                    && agent.getDepartement().getId().equals(fiche.getDepartementDesigne().getId());
            case AGENT_CJ -> fiche.getAgentDesigne() != null
                    && agent.getId().equals(fiche.getAgentDesigne().getId());
            case BRPD -> securityUtils.hasRole("AGENT_BRPD");
        };
        if (!autorise) {
            throw new BusinessException(
                    "Accès refusé — ce dossier ne vous a pas été affecté via la fiche d'affectation");
        }
    }

    private List<Agent> resolveDestinataires(FicheAffectationAffectationRequest request) {
        return switch (request.getTypeDesignation()) {
            case DEPARTEMENT -> getDepartementEligibleOrThrow(request.getDepartementDesigneId())
                    .getAgents().stream().filter(Agent::getActif).toList();
            case AGENT_CJ -> List.of(getConseillerJuridiqueOrThrow(request.getAgentDesigneId()));
            case BRPD -> resolveActiveAgentsByRole("AGENT_BRPD");
        };
    }

    private void notifierDestinataires(FicheAffectation fiche, List<Agent> destinataires) {
        if (destinataires.isEmpty()) {
            log.warn("[FicheAffectation] Aucun destinataire résolu pour la notification — dossier: {}",
                    fiche.getDossier().getId());
            return;
        }
        Map<String, String> placeholders = Map.of(
                "numero", fiche.getDossier().getNumber(),
                "departementOuAgent", libelleDesignation(fiche));
        String subject = portalConfigService.resolveNotificationText(
                "notif_subject_affectation_dossier", placeholders);
        String content = portalConfigService.resolveNotificationText(
                "notif_content_affectation_dossier", placeholders);

        for (Agent destinataire : destinataires) {
            Notification notification = Notification.builder()
                    .dossier(fiche.getDossier())
                    .type(NotificationType.AFFECTATION_DOSSIER)
                    .channel(NotificationChannel.PORTAL)
                    .recipient(destinataire.getKeycloakId())
                    .subject(subject)
                    .content(content)
                    .scheduledAt(Instant.now())
                    .build();
            notificationRepository.save(notification);
        }
        log.info("[FicheAffectation] Notification d'affectation envoyée — dossier: {}, destinataires: {}",
                fiche.getDossier().getId(), destinataires.size());
    }

    private String libelleDesignation(FicheAffectation fiche) {
        return switch (fiche.getTypeDesignation()) {
            case DEPARTEMENT -> "Département " + fiche.getDepartementDesigne().getLibelle();
            case AGENT_CJ -> fiche.getAgentDesigne().getNomComplet() + " (Conseiller Juridique)";
            case BRPD -> "BRPD";
        };
    }

    private List<Agent> resolveActiveAgentsByRole(String roleName) {
        List<Agent> agents = new ArrayList<>();
        for (String keycloakId : keycloakAdminService.getUserIdsByRole(roleName)) {
            agentRepository.findByKeycloakId(keycloakId)
                    .filter(Agent::getActif)
                    .ifPresent(agents::add);
        }
        return agents;
    }

    private Departement getDepartementEligibleOrThrow(UUID departementId) {
        if (departementId == null) {
            throw new BusinessException(
                    "Le département désigné est requis quand le type de désignation est DEPARTEMENT.");
        }
        Departement departement = departementRepository.findById(departementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Département introuvable : " + departementId));
        if (!DEPARTEMENTS_ELIGIBLES.contains(departement.getCode())) {
            throw new BusinessException(
                    "Seuls les départements DEI et DAC peuvent être désignés via la fiche d'affectation, reçu : "
                            + departement.getCode());
        }
        return departement;
    }

    private Agent getConseillerJuridiqueOrThrow(UUID agentId) {
        if (agentId == null) {
            throw new BusinessException(
                    "L'agent désigné est requis quand le type de désignation est AGENT_CJ.");
        }
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable : " + agentId));
        if (!Boolean.TRUE.equals(agent.getActif())) {
            throw new BusinessException("L'agent désigné doit être actif : " + agentId);
        }
        if (agent.getKeycloakId() == null
                || !keycloakAdminService.getUserRoles(agent.getKeycloakId()).contains("CONSEILLER_JURIDIQUE")) {
            throw new BusinessException(
                    "L'agent désigné doit avoir le rôle Conseiller Juridique : " + agentId);
        }
        return agent;
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
