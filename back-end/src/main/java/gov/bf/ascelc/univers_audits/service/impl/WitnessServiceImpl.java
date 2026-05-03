package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.WitnessRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.service.WitnessService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WitnessServiceImpl implements WitnessService {

    private final WitnessRepository    witnessRepository;
    private final DossierRepository    dossierRepository;
    private final DossierDetailsMapper detailsMapper;

    @Override
    public List<WitnessResponse> findByDossierId(UUID dossierId) {
        getDossierOrThrow(dossierId);
        return witnessRepository
                .findByDossierId(dossierId)
                .stream()
                .map(detailsMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public WitnessResponse create(UUID dossierId, WitnessRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);

        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Impossible d'ajouter un témoin à un dossier clôturé");
        }

        Witness witness = Witness.builder()
                .dossier(dossier)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .profession(request.getProfession())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .address(request.getAddress())
                .testimonyNature(request.getTestimonyNature())
                .relationWithParties(request.getRelationWithParties())
                .interrogationDate(request.getInterrogationDate())
                .consentToContact(Boolean.TRUE.equals(
                        request.getConsentToContact()))
                .anonymous(Boolean.TRUE.equals(request.getAnonymous()))
                .build();

        Witness saved = witnessRepository.save(witness);
        log.info("Témoin ajouté — dossier: {}, témoin: {}",
                dossierId, saved.getId());
        return detailsMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public WitnessResponse update(UUID dossierId,
                                  UUID witnessId,
                                  WitnessRequest request) {
        getDossierOrThrow(dossierId);
        Witness witness = getWitnessOrThrow(witnessId, dossierId);

        witness.setFirstName(request.getFirstName());
        witness.setLastName(request.getLastName());
        witness.setProfession(request.getProfession());
        witness.setPhoneNumber(request.getPhoneNumber());
        witness.setEmail(request.getEmail());
        witness.setAddress(request.getAddress());
        witness.setTestimonyNature(request.getTestimonyNature());
        witness.setRelationWithParties(request.getRelationWithParties());
        witness.setInterrogationDate(request.getInterrogationDate());
        if (request.getConsentToContact() != null) {
            witness.setConsentToContact(request.getConsentToContact());
        }
        if (request.getAnonymous() != null) {
            witness.setAnonymous(request.getAnonymous());
        }

        Witness saved = witnessRepository.save(witness);
        log.info("Témoin mis à jour — id: {}", witnessId);
        return detailsMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID dossierId, UUID witnessId) {
        getDossierOrThrow(dossierId);
        Witness witness = getWitnessOrThrow(witnessId, dossierId);
        witnessRepository.delete(witness);
        log.info("Témoin supprimé — id: {}", witnessId);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }

    private Witness getWitnessOrThrow(UUID witnessId, UUID dossierId) {
        Witness witness = witnessRepository.findById(witnessId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Témoin introuvable : " + witnessId));

        if (!witness.getDossier().getId().equals(dossierId)) {
            throw new BusinessException(
                    "Ce témoin n'appartient pas au dossier spécifié");
        }
        return witness;
    }
}