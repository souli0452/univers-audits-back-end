package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.model.entity.StatusHistory;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Écriture des traces d'audit d'un dossier (observation interne, historique de
 * statut) — logique auparavant dupliquée à l'identique dans DossierServiceImpl
 * et InvestigationServiceImpl.
 */
@Component
@RequiredArgsConstructor
public class DossierAuditRecorder {

    private final ObservationRepository   observationRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public void addObservation(Dossier dossier, ObservationType type,
                               String content, boolean confidential,
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

    public void recordStatusChange(Dossier dossier, DossierStatus previous,
                                   DossierStatus next, String reason,
                                   Agent agent, String ipAddress) {
        StatusHistory history = StatusHistory.builder()
                .dossier(dossier)
                .previousStatus(previous)
                .newStatus(next)
                .reason(reason)
                .agent(agent)
                .agentFullName(agent != null ? agent.getNomComplet() : "Système")
                .ipAddress(ipAddress)
                .build();
        statusHistoryRepository.save(history);
    }
}
