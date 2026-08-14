package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RelanceSuitesResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TransmissionAutoriteRepository;
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
public class TransmissionAutoriteService {

    private final TransmissionAutoriteRepository transmissionAutoriteRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public TransmissionAutoriteResponse creer(UUID investigationId, TransmissionAutoriteRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "La transmission n'est possible qu'après la décision finale du CGE.");
        }
        if (transmissionAutoriteRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Ce dossier a déjà été transmis à une autorité.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire(request.getAutoriteDestinataire())
                .transmittedAt(Instant.now())
                .transmittedBy(agent)
                .build();

        TransmissionAutorite saved = transmissionAutoriteRepository.save(transmission);
        log.info("Transmission à l'autorité enregistrée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    public TransmissionAutoriteResponse getOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucune transmission enregistrée pour cette investigation : " + investigationId);
        }

        TransmissionAutorite transmission = transmissionAutoriteRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune transmission enregistrée pour cette investigation : " + investigationId));
        return toResponse(transmission);
    }

    @Transactional
    public TransmissionAutoriteResponse ajouterRelance(UUID investigationId, RelanceSuitesRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        TransmissionAutorite transmission = transmissionAutoriteRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucune transmission enregistrée pour cette investigation — "
                                + "impossible d'ajouter une relance."));

        Agent agent = agentContextResolver.getCurrentAgent();
        RelanceSuites relance = RelanceSuites.builder()
                .transmissionAutorite(transmission)
                .relanceAt(Instant.now())
                .agent(agent)
                .contenu(request.getContenu())
                .build();
        transmission.getRelances().add(relance);

        TransmissionAutorite saved = transmissionAutoriteRepository.save(transmission);
        log.info("Relance ajoutée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private TransmissionAutoriteResponse toResponse(TransmissionAutorite t) {
        Instant relanceDueAt = resolveDeadline(t.getTransmittedAt(), "RELANCE_SUITES_TRANSMISSION");
        boolean relanceOverdue = relanceDueAt != null
                && t.getRelances().isEmpty()
                && Instant.now().isAfter(relanceDueAt);

        return TransmissionAutoriteResponse.builder()
                .id(t.getId())
                .investigationId(t.getInvestigation().getId())
                .autoriteDestinataire(t.getAutoriteDestinataire())
                .transmittedAt(t.getTransmittedAt())
                .transmittedByNom(t.getTransmittedBy().getNomComplet())
                .relanceDueAt(relanceDueAt)
                .relanceOverdue(relanceOverdue)
                .relances(t.getRelances().stream().map(this::toRelanceResponse).toList())
                .build();
    }

    private RelanceSuitesResponse toRelanceResponse(RelanceSuites r) {
        return RelanceSuitesResponse.builder()
                .id(r.getId())
                .relanceAt(r.getRelanceAt())
                .agentNom(r.getAgent().getNomComplet())
                .contenu(r.getContenu())
                .build();
    }

    private Instant resolveDeadline(Instant from, String delaiCode) {
        try {
            int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
            return from.plusSeconds((long) delaiJours * 24 * 3600);
        } catch (ResourceNotFoundException e) {
            log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
            return null;
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
