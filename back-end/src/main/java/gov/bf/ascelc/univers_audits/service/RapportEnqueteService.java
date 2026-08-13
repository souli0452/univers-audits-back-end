package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RapportEnqueteService {

    private final RapportEnqueteRepository rapportEnqueteRepository;
    private final NoteRecommandationsRepository noteRecommandationsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;

    @Transactional
    public RapportEnquete enregistrerRapport(UUID investigationId, RapportEnqueteRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkEditable(investigation);

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseGet(() -> RapportEnquete.builder().investigation(investigation).build());

        rapport.setTitre(request.getTitre());
        rapport.setIntroduction(request.getIntroduction());
        rapport.setMethodologie(request.getMethodologie());
        rapport.setInformationsCollectees(request.getInformationsCollectees());
        rapport.setExposeFactuelAnomalies(request.getExposeFactuelAnomalies());
        rapport.setQuantificationPrejudice(request.getQuantificationPrejudice());
        rapport.setReserves(request.getReserves());
        rapport.setConclusions(request.getConclusions());

        RapportEnquete saved = rapportEnqueteRepository.save(rapport);
        log.info("Rapport d'enquête enregistré — investigation: {}", investigationId);
        return saved;
    }

    public RapportEnquete getRapportOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun rapport d'enquête n'a été rédigé pour cette investigation : "
                                + investigationId));

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucun rapport d'enquête n'a été rédigé pour cette investigation : "
                            + investigationId);
        }

        return rapport;
    }

    @Transactional
    public NoteRecommandations enregistrerNote(UUID investigationId, NoteRecommandationsRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkEditable(investigation);

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Rédigez d'abord le rapport d'enquête "
                                + "(PUT /investigations/{id}/rapport) avant la note de recommandations."));

        NoteRecommandations note = noteRecommandationsRepository
                .findByRapportEnqueteId(rapport.getId())
                .orElseGet(() -> NoteRecommandations.builder().rapportEnquete(rapport).build());

        note.setContenu(request.getContenu());

        NoteRecommandations saved = noteRecommandationsRepository.save(note);
        log.info("Note de recommandations enregistrée — investigation: {}", investigationId);
        return saved;
    }

    public NoteRecommandations getNoteOrThrow(UUID investigationId) {
        RapportEnquete rapport = getRapportOrThrow(investigationId);
        return noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune note de recommandations n'a été rédigée pour cette investigation : "
                                + investigationId));
    }

    private void checkEditable(Investigation investigation) {
        if (investigation.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Le rapport d'enquête et la note de recommandations ne sont modifiables "
                            + "que pendant que l'investigation est en cours.");
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
