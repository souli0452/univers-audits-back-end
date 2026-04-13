package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.*;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.InvestigationService;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestigationServiceImpl
        implements InvestigationService {

    private final InvestigationRepository       investigationRepository;
    private final InvestigationMemberRepository memberRepository;
    private final DossierRepository             dossierRepository;
    private final AgentRepository               agentRepository;
    private final ObservationRepository         observationRepository;
    private final StatusHistoryRepository       statusHistoryRepository;
    private final InvestigationMapper           investigationMapper;
    private final SecurityUtils                 securityUtils;


    @Override
    public InvestigationResponse findById(UUID id) {
        return investigationMapper.toResponse(
                getInvestigationOrThrow(id));
    }

    @Override
    public InvestigationResponse findByDossierId(UUID dossierId) {
        Investigation inv = investigationRepository
                .findByDossierId(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune investigation pour ce dossier"));
        return investigationMapper.toResponse(inv);
    }

    @Override
    public Page<InvestigationResponse> findAll(Pageable pageable) {
        return investigationRepository.findAll(pageable)
                .map(investigationMapper::toResponse);
    }

    @Override
    public Page<InvestigationResponse> findOverdue(
            Pageable pageable) {

        return investigationRepository
                .findAll(pageable)
                .map(investigationMapper::toResponse);
    }

    @Override
    @Transactional
    public InvestigationResponse open(
            UUID dossierId,
            InvestigationCreateRequest request,
            String ipAddress) {


        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));

        if (dossier.getStatus() != DossierStatus.RECEVABLE) {
            throw new BusinessException(
                    "Une investigation ne peut être ouverte "
                            + "que sur un dossier déclaré recevable. "
                            + "Statut actuel : " + dossier.getStatus());
        }

        if (investigationRepository.existsByDossierId(dossierId)) {
            throw new BusinessException(
                    "Une investigation existe déjà pour ce dossier");
        }


        Agent cgea = getCurrentAgent();


        Investigation investigation = Investigation.builder()
                .dossier(dossier)
                .cgea(cgea)
                .status(InvestigationStatus.INITIATED)
                .plannedDurationDays(
                        request.getPlannedDurationDays() != null
                                ? request.getPlannedDurationDays()
                                : 90)
                .build();

        Investigation saved = investigationRepository
                .save(investigation);


        dossier.setStatus(DossierStatus.EN_INVESTIGATION);
        dossierRepository.save(dossier);


        recordDossierStatusChange(dossier,
                DossierStatus.RECEVABLE,
                DossierStatus.EN_INVESTIGATION,
                "Investigation ouverte par le CGEA",
                cgea, ipAddress);


        addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Investigation ouverte. Durée prévue : "
                        + saved.getPlannedDurationDays()
                        + " jours.",
                true, cgea);

        log.info("Investigation ouverte — dossier: {}",
                dossier.getNumber());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse start(UUID investigationId,
                                       String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);


        boolean hasLeader = memberRepository
                .existsByInvestigationIdAndAgentIdAndActiveTrue(
                        investigationId,
                        getCurrentAgent().getId());

        long memberCount = memberRepository
                .countByInvestigationIdAndActiveTrue(
                        investigationId);

        if (memberCount == 0) {
            throw new BusinessException(
                    "L'équipe d'investigation doit avoir "
                            + "au moins un membre avant le démarrage");
        }


        inv.start();

        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation démarrée. Date de fin prévue : "
                        + saved.getPlannedEndDate(),
                true, getCurrentAgent());

        log.info("Investigation {} démarrée — fin prévue: {}",
                investigationId, saved.getPlannedEndDate());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse suspend(UUID investigationId,
                                         String reason,
                                         String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);

        if (inv.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Seule une investigation en cours "
                            + "peut être suspendue");
        }


        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    "Le motif de suspension est obligatoire");
        }

        inv.suspend(reason);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation suspendue. Motif : " + reason,
                true, getCurrentAgent());

        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse resume(UUID investigationId,
                                        String reason,
                                        String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);

        if (inv.getStatus() != InvestigationStatus.SUSPENDED) {
            throw new BusinessException(
                    "Seule une investigation suspendue "
                            + "peut être reprise");
        }

        inv.setStatus(InvestigationStatus.IN_PROGRESS);
        inv.setSuspensionReason(null);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation reprise. " + (reason != null
                        ? reason : ""),
                true, getCurrentAgent());

        return investigationMapper.toResponse(saved);
    }


    @Override
    @Transactional
    public InvestigationResponse extendDeadline(
            UUID investigationId,
            ExtendDeadlineRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);
        Agent cgea = getCurrentAgent();

        // Extension enregistrée avec trace complète
        inv.extendDeadline(
                request.getNewDeadline(),
                request.getReason(),
                cgea);

        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Délai d'investigation étendu jusqu'au "
                        + request.getNewDeadline()
                        + ". Motif : " + request.getReason(),
                true, cgea);

        log.info("Délai investigation {} étendu au {}",
                investigationId, request.getNewDeadline());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse submitReport(
            UUID investigationId,
            InvestigationUpdateRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);

        if (inv.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Le rapport ne peut être soumis "
                            + "que pour une investigation en cours");
        }

        inv.setFinalReport(request.getFinalReport());
        inv.setConclusions(request.getConclusions());
        inv.setRecommendations(request.getRecommendations());
        inv.setOutcome(request.getOutcome());
        inv.complete();


        Dossier dossier = inv.getDossier();
        dossier.setStatus(DossierStatus.RAPPORT_PRODUIT);
        dossierRepository.save(dossier);

        recordDossierStatusChange(dossier,
                DossierStatus.EN_INVESTIGATION,
                DossierStatus.RAPPORT_PRODUIT,
                "Rapport d'investigation soumis",
                getCurrentAgent(), ipAddress);

        Investigation saved = investigationRepository.save(inv);

        addObservation(dossier,
                ObservationType.FIELD_FINDING,
                "Rapport final soumis. Conclusions : "
                        + request.getConclusions(),
                true, getCurrentAgent());

        log.info("Rapport soumis — investigation: {}",
                investigationId);
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveDei(UUID investigationId,
                                            String ipAddress) {
        Investigation inv = getInvestigationOrThrow(
                investigationId);
        Agent agent = getCurrentAgent();


        inv.setDeiApprovedAt(Instant.now());
        inv.setDeiApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport approuvé par le DEI.",
                true, agent);

        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveLegalAdvisor(
            UUID investigationId, String ipAddress) {
        Investigation inv = getInvestigationOrThrow(
                investigationId);
        Agent agent = getCurrentAgent();

        // Conseiller juridique dispose de 10 jours ouvrables
        inv.setLegalAdvisorApprovedAt(Instant.now());
        inv.setLegalAdvisorApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.ADMISSIBILITY_ANALYSIS,
                "Rapport approuvé par le conseiller juridique.",
                true, agent);

        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveCge(UUID investigationId,
                                            String reason,
                                            String ipAddress) {
        Investigation inv = getInvestigationOrThrow(
                investigationId);
        Agent cge = getCurrentAgent();

        inv.setCgeApprovedAt(Instant.now());
        inv.setCgeApprovedBy(cge);
        inv.setStatus(InvestigationStatus.ARCHIVED);

        Dossier dossier = inv.getDossier();
        dossier.setStatus(DossierStatus.DECISION_RENDUE);
        dossierRepository.save(dossier);

        recordDossierStatusChange(dossier,
                DossierStatus.RAPPORT_PRODUIT,
                DossierStatus.DECISION_RENDUE,
                "Décision finale CGE : " + reason,
                cge, ipAddress);

        Investigation saved = investigationRepository.save(inv);

        addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Décision finale rendue par le CGE. " + reason,
                true, cge);

        log.info("Décision CGE rendue — dossier: {}",
                dossier.getNumber());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse addMember(
            UUID investigationId,
            AddMemberRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);

        if (inv.getStatus() == InvestigationStatus.COMPLETED
                || inv.getStatus()
                == InvestigationStatus.ARCHIVED) {
            throw new BusinessException(
                    "Impossible d'ajouter un membre à une "
                            + "investigation terminée");
        }

        // Vérifier que l'agent n'est pas déjà dans l'équipe
        if (memberRepository
                .existsByInvestigationIdAndAgentIdAndActiveTrue(
                        investigationId, request.getAgentId())) {
            throw new BusinessException(
                    "Cet agent est déjà membre de cette "
                            + "investigation");
        }

        Agent agent = agentRepository
                .findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent introuvable : "
                                + request.getAgentId()));

        Agent currentAgent = getCurrentAgent();

        InvestigationMember member = InvestigationMember.builder()
                .investigation(inv)
                .agent(agent)
                .teamRole(request.getTeamRole())
                .assignedBy(currentAgent.getKeycloakId())
                .active(true)
                .build();

        memberRepository.save(member);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre ajouté à l'équipe : "
                        + agent.getNomComplet()
                        + " (" + request.getTeamRole() + ")",
                true, currentAgent);

        return investigationMapper.toResponse(
                getInvestigationOrThrow(investigationId));
    }

    @Override
    @Transactional
    public InvestigationResponse removeMember(
            UUID investigationId,
            UUID agentId,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(
                investigationId);
        Agent currentAgent = getCurrentAgent();


        var members = memberRepository
                .findByInvestigationIdAndActiveTrue(investigationId);

        InvestigationMember member = members.stream()
                .filter(m -> m.getAgent().getId().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membre introuvable dans cette équipe"));

        member.setActive(false);
        memberRepository.save(member);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre retiré de l'équipe : "
                        + member.getAgent().getNomComplet(),
                true, currentAgent);

        return investigationMapper.toResponse(
                getInvestigationOrThrow(investigationId));
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private Agent getCurrentAgent() {
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException(
                        "Agent non authentifié"));
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. "
                                + "Contactez l'administrateur DDIC."));
    }

    private void addObservation(Dossier dossier,
                                ObservationType type,
                                String content,
                                boolean confidential,
                                Agent agent) {
        Observation obs = Observation.builder()
                .dossier(dossier)
                .type(type)
                .content(content)
                .confidential(confidential)
                .author(agent)
                .authorFullName(agent.getNomComplet())
                .statusSnapshot(dossier.getStatus())
                .build();
        observationRepository.save(obs);
    }

    private void recordDossierStatusChange(Dossier dossier,
                                           DossierStatus previous,
                                           DossierStatus next,
                                           String reason,
                                           Agent agent,
                                           String ipAddress) {
        StatusHistory history = StatusHistory.builder()
                .dossier(dossier)
                .previousStatus(previous)
                .newStatus(next)
                .reason(reason)
                .agent(agent)
                .agentFullName(agent.getNomComplet())
                .ipAddress(ipAddress)
                .build();
        statusHistoryRepository.save(history);
    }
}