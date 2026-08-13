package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ChecklistDossierTravailCocheRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistDossierTravailService {

    private final InvestigationRepository investigationRepository;
    private final PointChecklistDossierTravailRepository pointRepository;
    private final ChecklistDossierTravailCocheRepository cocheRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    public List<ChecklistDossierTravailItemResponse> getChecklist(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        boolean maskSensitive = Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential();

        List<PointChecklistDossierTravail> points = pointRepository.findByActifTrueOrderByOrdreAsc();
        Map<UUID, ChecklistDossierTravailCoche> etatParPoint = cocheRepository
                .findByInvestigationId(investigationId).stream()
                .collect(Collectors.toMap(c -> c.getPoint().getId(), c -> c));

        return points.stream()
                .map(point -> toItemResponse(point, etatParPoint.get(point.getId()), maskSensitive))
                .toList();
    }

    @Transactional
    public ChecklistDossierTravailItemResponse setCoche(
            UUID investigationId, String pointCode, ChecklistCocheRequest request) {

        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkEditable(investigation);

        PointChecklistDossierTravail point = pointRepository.findByCode(pointCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Point de check-list introuvable : " + pointCode));

        ChecklistDossierTravailCoche etat = cocheRepository
                .findByInvestigationIdAndPointId(investigationId, point.getId())
                .orElseGet(() -> ChecklistDossierTravailCoche.builder()
                        .investigation(investigation)
                        .point(point)
                        .build());

        etat.setCoche(request.getCoche());
        etat.setCommentaire(request.getCommentaire());
        if (Boolean.TRUE.equals(request.getCoche())) {
            etat.setCochePar(agentContextResolver.getCurrentAgent());
            etat.setCocheAt(Instant.now());
        }

        ChecklistDossierTravailCoche saved = cocheRepository.save(etat);
        log.info("Check-list dossier de travail — investigation {}, point {}, coché={}",
                investigationId, pointCode, request.getCoche());
        return toItemResponse(point, saved, false);
    }

    /** Utilisé par InvestigationServiceImpl.submitReport() — aucune vérification
     *  d'habilitation ici, cette méthode est un simple calcul de complétude. */
    public boolean isComplete(UUID investigationId) {
        long actifs = pointRepository.findByActifTrueOrderByOrdreAsc().size();
        if (actifs == 0) {
            return true;
        }
        long coches = cocheRepository.countByInvestigationIdAndCocheTrueAndPointActifTrue(investigationId);
        return coches == actifs;
    }

    private void checkEditable(Investigation investigation) {
        if (investigation.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "La check-list du dossier de travail n'est modifiable "
                            + "que pendant que l'investigation est en cours.");
        }
    }

    private ChecklistDossierTravailItemResponse toItemResponse(
            PointChecklistDossierTravail point, ChecklistDossierTravailCoche etat, boolean maskSensitive) {
        return ChecklistDossierTravailItemResponse.builder()
                .pointId(point.getId())
                .code(point.getCode())
                .libelle(point.getLibelle())
                .categorie(point.getCategorie())
                .ordre(point.getOrdre())
                .coche(etat != null && Boolean.TRUE.equals(etat.getCoche()))
                .cocheParNom(!maskSensitive && etat != null && etat.getCochePar() != null
                        ? etat.getCochePar().getNomComplet() : null)
                .cocheAt(etat != null ? etat.getCocheAt() : null)
                .commentaire(!maskSensitive && etat != null ? etat.getCommentaire() : null)
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
