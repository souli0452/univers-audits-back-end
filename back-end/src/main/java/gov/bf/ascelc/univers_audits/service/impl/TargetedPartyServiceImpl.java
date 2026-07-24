package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.TargetedPartyRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TargetedPartyResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.TargetedParty;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.service.TargetedPartyService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
public class TargetedPartyServiceImpl implements TargetedPartyService {

    private final TargetedPartyRepository targetedPartyRepository;
    private final DossierDetailsMapper    detailsMapper;
    private final DossierAccessGuard      accessGuard;

    @Override
    public List<TargetedPartyResponse> findByDossierId(UUID dossierId) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        // Un dossier confidentiel masque entièrement ses parties visées aux rôles
        // non habilités — même comportement que DossierServiceImpl.maskSensitiveData.
        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return targetedPartyRepository
                .findByDossierId(dossierId)
                .stream()
                .map(detailsMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public TargetedPartyResponse create(UUID dossierId,
                                        TargetedPartyRequest request) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);

        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Impossible d'ajouter une partie à un dossier clôturé");
        }

        TargetedParty party = TargetedParty.builder()
                .dossier(dossier)
                .partyType(request.getPartyType())
                .firstName(request.getFirstName())
                .name(request.getName())
                .position(request.getPosition())
                .institution(request.getInstitution())
                .organization(request.getOrganization())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .relationWithDeclarant(request.getRelationWithDeclarant())
                .allegedRole(request.getAllegedRole())
                .build();

        TargetedParty saved = targetedPartyRepository.save(party);
        log.info("Partie visée ajoutée — dossier: {}, partie: {}",
                dossierId, saved.getId());
        return detailsMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TargetedPartyResponse update(UUID dossierId,
                                        UUID partyId,
                                        TargetedPartyRequest request) {
        accessGuard.getDossierOrThrow(dossierId);
        TargetedParty party = getPartyOrThrow(partyId, dossierId);

        party.setPartyType(request.getPartyType());
        party.setFirstName(request.getFirstName());
        party.setName(request.getName());
        party.setPosition(request.getPosition());
        party.setInstitution(request.getInstitution());
        party.setOrganization(request.getOrganization());
        party.setAddress(request.getAddress());
        party.setPhoneNumber(request.getPhoneNumber());
        party.setEmail(request.getEmail());
        party.setRelationWithDeclarant(request.getRelationWithDeclarant());
        party.setAllegedRole(request.getAllegedRole());

        TargetedParty saved = targetedPartyRepository.save(party);
        log.info("Partie visée mise à jour — id: {}", partyId);
        return detailsMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID dossierId, UUID partyId) {
        accessGuard.getDossierOrThrow(dossierId);
        TargetedParty party = getPartyOrThrow(partyId, dossierId);
        targetedPartyRepository.delete(party);
        log.info("Partie visée supprimée — id: {}", partyId);
    }

    private TargetedParty getPartyOrThrow(UUID partyId, UUID dossierId) {
        TargetedParty party = targetedPartyRepository.findById(partyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Partie visée introuvable : " + partyId));

        if (!party.getDossier().getId().equals(dossierId)) {
            throw new BusinessException(
                    "Cette partie n'appartient pas au dossier spécifié");
        }
        return party;
    }
}
