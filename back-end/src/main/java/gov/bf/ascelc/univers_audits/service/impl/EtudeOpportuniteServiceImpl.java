package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.EtudeOpportuniteRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.service.EtudeOpportuniteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtudeOpportuniteServiceImpl implements EtudeOpportuniteService {

    private final EtudeOpportuniteRepository etudeOpportuniteRepository;
    private final TypeInfractionRepository   typeInfractionRepository;
    private final DossierDetailsMapper       detailsMapper;
    private final DossierAccessGuard         accessGuard;

    @Override
    public EtudeOpportuniteResponse findByDossierId(UUID dossierId) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        return etudeOpportuniteRepository.findByDossierId(dossierId)
                .map(detailsMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public EtudeOpportuniteResponse upsert(UUID dossierId, EtudeOpportuniteRequest request) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        if (dossier.getStatus() != DossierStatus.EN_ETUDE_OPPORTUNITE) {
            throw new BusinessException(
                    "L'étude d'opportunité ne peut être modifiée que pendant "
                            + "l'étape d'étude d'opportunité (statut actuel : "
                            + dossier.getStatus() + ")");
        }

        EtudeOpportunite etude = etudeOpportuniteRepository.findByDossierId(dossierId)
                .orElseGet(() -> EtudeOpportunite.builder().dossier(dossier).build());

        detailsMapper.updateEntity(request, etude);

        if (request.getTypeInfractionId() != null) {
            TypeInfraction typeInfraction = typeInfractionRepository
                    .findById(request.getTypeInfractionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Qualification pénale introuvable : "
                                    + request.getTypeInfractionId()));
            etude.setTypeInfraction(typeInfraction);
        }

        EtudeOpportunite saved = etudeOpportuniteRepository.save(etude);
        log.info("Étude d'opportunité mise à jour — dossier: {}", dossierId);
        return detailsMapper.toResponse(saved);
    }
}
