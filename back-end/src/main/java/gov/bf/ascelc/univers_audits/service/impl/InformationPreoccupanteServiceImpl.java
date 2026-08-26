package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.dto.request.DeclarantCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupante;
import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupanteDossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteDossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteRepository;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.service.InformationPreoccupanteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InformationPreoccupanteServiceImpl implements InformationPreoccupanteService {

    private final InformationPreoccupanteRepository informationPreoccupanteRepository;
    private final InformationPreoccupanteDossierRepository informationPreoccupanteDossierRepository;
    private final DossierRepository dossierRepository;
    private final DossierService dossierService;

    @Override
    public InformationPreoccupanteResponse create(InformationPreoccupanteCreateRequest request) {
        InformationPreoccupante entity = InformationPreoccupante.builder()
                .objet(request.getObjet())
                .description(request.getDescription())
                .source(request.getSource())
                .sourceReference(request.getSourceReference())
                .dateReception(request.getDateReception())
                .statut(StatutInformationPreoccupante.NOUVELLE)
                .build();

        InformationPreoccupante saved = informationPreoccupanteRepository.save(entity);
        return toResponse(saved, List.of());
    }

    @Override
    public InformationPreoccupanteResponse findById(UUID id) {
        InformationPreoccupante entity = getOrThrow(id);
        List<InformationPreoccupanteDossier> links =
                informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(id);
        return toResponse(entity, links);
    }

    @Override
    public Page<InformationPreoccupanteResponse> findAll(Pageable pageable) {
        return informationPreoccupanteRepository.findAllByOrderByDateReceptionDesc(pageable)
                .map(entity -> toResponse(entity,
                        informationPreoccupanteDossierRepository
                                .findByInformationPreoccupanteId(entity.getId())));
    }

    @Override
    public InformationPreoccupanteResponse rattacherDossier(
            UUID informationPreoccupanteId, UUID dossierId, RattacherDossierRequest request) {

        InformationPreoccupante info = getOrThrow(informationPreoccupanteId);

        if (info.getStatut() == StatutInformationPreoccupante.CLASSEE_SANS_SUITE) {
            throw new BusinessException(
                    "Impossible de rattacher un dossier à une information classée sans suite.");
        }

        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));

        if (informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(informationPreoccupanteId, dossierId)) {
            throw new BusinessException(
                    "Ce dossier est déjà rattaché à cette information préoccupante.");
        }

        InformationPreoccupanteDossier link = InformationPreoccupanteDossier.builder()
                .informationPreoccupante(info)
                .dossier(dossier)
                .commentaire(request != null ? request.getCommentaire() : null)
                .build();
        informationPreoccupanteDossierRepository.save(link);

        if (info.getStatut() == StatutInformationPreoccupante.NOUVELLE) {
            info.setStatut(StatutInformationPreoccupante.RATTACHEE);
            informationPreoccupanteRepository.save(info);
        }

        List<InformationPreoccupanteDossier> links = informationPreoccupanteDossierRepository
                .findByInformationPreoccupanteId(informationPreoccupanteId);
        return toResponse(info, links);
    }

    @Override
    public DossierResponse declencherAutoSaisine(UUID informationPreoccupanteId, String ipAddress) {

        InformationPreoccupante info = getOrThrow(informationPreoccupanteId);

        if (info.getStatut() == StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE) {
            throw new BusinessException(
                    "Une auto-saisine a déjà été déclenchée pour cette information préoccupante.");
        }
        if (info.getStatut() == StatutInformationPreoccupante.CLASSEE_SANS_SUITE) {
            throw new BusinessException(
                    "Impossible de déclencher une auto-saisine depuis une information classée sans suite.");
        }

        DossierCreateRequest dossierRequest = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.AUDIT_REPORT)
                .autoReferralSource(info.getSource())
                .object(info.getObjet())
                .description(info.getDescription())
                .declarantData(DeclarantCreateRequest.builder()
                        .typeDeclarant(TypeDeclarant.ASCE_SELF_REFERRAL)
                        .build())
                .build();

        DossierResponse created = dossierService.submit(dossierRequest, ipAddress);

        Dossier dossier = dossierRepository.findById(created.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier nouvellement créé introuvable : " + created.getId()));

        InformationPreoccupanteDossier link = InformationPreoccupanteDossier.builder()
                .informationPreoccupante(info)
                .dossier(dossier)
                .commentaire("Dossier créé par déclenchement d'auto-saisine")
                .build();
        informationPreoccupanteDossierRepository.save(link);

        info.setStatut(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        informationPreoccupanteRepository.save(info);

        return created;
    }

    @Override
    public InformationPreoccupanteResponse classerSansSuite(UUID informationPreoccupanteId) {

        InformationPreoccupante info = getOrThrow(informationPreoccupanteId);

        if (info.getStatut() == StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE) {
            throw new BusinessException(
                    "Impossible de classer sans suite une information ayant déjà déclenché une auto-saisine.");
        }

        info.setStatut(StatutInformationPreoccupante.CLASSEE_SANS_SUITE);
        InformationPreoccupante saved = informationPreoccupanteRepository.save(info);

        List<InformationPreoccupanteDossier> links = informationPreoccupanteDossierRepository
                .findByInformationPreoccupanteId(informationPreoccupanteId);
        return toResponse(saved, links);
    }

    private InformationPreoccupante getOrThrow(UUID id) {
        return informationPreoccupanteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Information préoccupante introuvable : " + id));
    }

    private InformationPreoccupanteResponse toResponse(
            InformationPreoccupante entity, List<InformationPreoccupanteDossier> links) {

        List<InformationPreoccupanteResponse.DossierRattacheResponse> dossiersRattaches = links.stream()
                .map(link -> InformationPreoccupanteResponse.DossierRattacheResponse.builder()
                        .dossierId(link.getDossier().getId())
                        .dossierNumber(link.getDossier().getNumber())
                        .commentaire(link.getCommentaire())
                        .linkedAt(link.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return InformationPreoccupanteResponse.builder()
                .id(entity.getId())
                .objet(entity.getObjet())
                .description(entity.getDescription())
                .source(entity.getSource())
                .sourceReference(entity.getSourceReference())
                .dateReception(entity.getDateReception())
                .statut(entity.getStatut())
                .createdAt(entity.getCreatedAt())
                .dossiersRattaches(dossiersRattaches)
                .build();
    }
}
