package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.mapper.SeanceCtadpMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SeanceCtadpCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpRepository;
import gov.bf.ascelc.univers_audits.service.SeanceCtadpService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeanceCtadpServiceImpl implements SeanceCtadpService {

    private final SeanceCtadpRepository        seanceCtadpRepository;
    private final SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    private final DossierRepository            dossierRepository;
    private final SeanceCtadpMapper             mapper;

    @Override
    @Transactional
    public SeanceCtadpResponse create(SeanceCtadpCreateRequest request) {
        SeanceCTADP seance = SeanceCTADP.builder()
                .dateSeance(request.getDateSeance())
                .participants(request.getParticipants())
                .build();

        SeanceCTADP saved = seanceCtadpRepository.save(seance);
        log.info("Séance CTADP créée — id: {}, date: {}", saved.getId(), saved.getDateSeance());
        return mapper.toResponse(saved);
    }

    @Override
    public SeanceCtadpResponse findById(UUID id) {
        return mapper.toResponse(getSeanceOrThrow(id));
    }

    @Override
    public Page<SeanceCtadpResponse> findAll(Pageable pageable) {
        // Vue liste : on évite volontairement toResponse (qui remplit la
        // collection lazy "dossiers" via fillDossiers) pour ne pas déclencher
        // un N+1 sur chaque séance de la page. mapToResponse ignore déjà
        // "dossiers" (@Mapping(target = "dossiers", ignore = true)) — on la
        // force explicitement à une liste vide pour un contrat DTO stable.
        return seanceCtadpRepository.findAll(pageable)
                .map(mapper::mapToResponse)
                .map(response -> {
                    response.setDossiers(List.of());
                    return response;
                });
    }

    @Override
    @Transactional
    public SeanceCtadpResponse addDossier(UUID seanceId, AddDossierToSeanceRequest request) {
        SeanceCTADP seance = getSeanceOrThrow(seanceId);

        if (seance.getStatut() != StatutSeanceCtadp.PLANIFIEE) {
            throw new BusinessException(
                    "Impossible d'ajouter un dossier à une séance qui n'est plus planifiée");
        }

        Dossier dossier = dossierRepository.findById(request.getDossierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + request.getDossierId()));

        if (dossier.getStatus() != DossierStatus.EN_REVUE_CTADP) {
            throw new BusinessException(
                    "Ce dossier n'est pas en attente de revue CTADP (statut actuel : "
                            + dossier.getStatus() + ")");
        }

        if (seanceCtadpDossierRepository.existsBySeanceCtadpIdAndDossierId(
                seanceId, request.getDossierId())) {
            throw new BusinessException(
                    "Ce dossier est déjà à l'ordre du jour de cette séance");
        }

        SeanceCtadpDossier entry = SeanceCtadpDossier.builder()
                .seanceCtadp(seance)
                .dossier(dossier)
                .build();
        seance.getDossiers().add(entry);

        SeanceCTADP saved = seanceCtadpRepository.save(seance);
        log.info("Dossier {} ajouté à l'ordre du jour de la séance {}",
                dossier.getId(), seanceId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SeanceCtadpResponse recordRecommandation(
            UUID seanceId, UUID dossierId, RecommandationCtadpRequest request) {

        getSeanceOrThrow(seanceId);

        SeanceCtadpDossier entry = seanceCtadpDossierRepository
                .findBySeanceCtadpIdAndDossierId(seanceId, dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ce dossier n'est pas à l'ordre du jour de cette séance"));

        entry.setRecommandation(request.getRecommandation());
        entry.setCommentaire(request.getCommentaire());
        seanceCtadpDossierRepository.save(entry);

        log.info("Recommandation enregistrée — séance: {}, dossier: {}, recommandation: {}",
                seanceId, dossierId, request.getRecommandation());
        return findById(seanceId);
    }

    @Override
    @Transactional
    public SeanceCtadpResponse tenir(UUID seanceId, TenirSeanceRequest request) {
        SeanceCTADP seance = getSeanceOrThrow(seanceId);

        if (seance.getStatut() != StatutSeanceCtadp.PLANIFIEE) {
            throw new BusinessException(
                    "Seule une séance planifiée peut être marquée tenue (statut actuel : "
                            + seance.getStatut() + ")");
        }

        seance.setStatut(StatutSeanceCtadp.TENUE);
        seance.setProcesVerbal(request.getProcesVerbal());

        SeanceCTADP saved = seanceCtadpRepository.save(seance);
        log.info("Séance CTADP {} marquée TENUE", seanceId);
        return mapper.toResponse(saved);
    }

    private SeanceCTADP getSeanceOrThrow(UUID id) {
        return seanceCtadpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Séance CTADP introuvable : " + id));
    }
}
