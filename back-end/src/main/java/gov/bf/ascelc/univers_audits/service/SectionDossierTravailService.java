package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SectionDossierTravailService {

    private static final List<TypeSectionDossierTravail> SECTIONS_FIXES = List.of(
            TypeSectionDossierTravail.ADMINISTRATION_MISSION,
            TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENTITE,
            TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENVIRONNEMENT);

    private final SectionDossierTravailRepository sectionRepository;
    private final DossierRepository dossierRepository;
    private final AttachmentRepository attachmentRepository;

    @Transactional
    public void creerSectionsFixes(Dossier dossier) {
        for (TypeSectionDossierTravail type : SECTIONS_FIXES) {
            if (!sectionRepository.existsByDossierIdAndType(dossier.getId(), type)) {
                sectionRepository.save(SectionDossierTravail.builder()
                        .dossier(dossier)
                        .type(type)
                        .build());
            }
        }
        log.info("Sections fixes du dossier de travail creees — dossier: {}",
                dossier.getNumber());
    }

    @Transactional
    public void definirOrganisationDetail(UUID dossierId, OrganisationDetail organisationDetail) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (dossier.getOrganisationDetail() != null) {
            throw new BusinessException(
                    "Le mode d'organisation du detail est deja defini pour ce dossier : "
                            + dossier.getOrganisationDetail());
        }
        dossier.setOrganisationDetail(organisationDetail);
        dossierRepository.save(dossier);
    }

    @Transactional
    public SectionDossierTravail creerSectionDetail(UUID dossierId, String libelle) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (dossier.getOrganisationDetail() == null) {
            throw new BusinessException(
                    "Definissez d'abord le mode d'organisation du detail "
                            + "(POST /organisation-detail) avant de creer une section.");
        }
        if (sectionRepository.existsByDossierIdAndTypeAndLibelle(
                dossierId, TypeSectionDossierTravail.DETAIL, libelle)) {
            throw new BusinessException(
                    "Une section DETAIL avec ce libelle existe deja pour ce dossier : "
                            + libelle);
        }
        return sectionRepository.save(SectionDossierTravail.builder()
                .dossier(dossier)
                .type(TypeSectionDossierTravail.DETAIL)
                .libelle(libelle)
                .build());
    }

    public List<SectionDossierTravailResponse> listerSections(UUID dossierId) {
        getDossierOrThrow(dossierId);
        return sectionRepository.findByDossierId(dossierId).stream()
                .map(s -> new SectionDossierTravailResponse(
                        s.getId(), s.getType(), s.getLibelle(),
                        attachmentRepository.countBySectionId(s.getId())))
                .toList();
    }

    public record SectionDossierTravailResponse(
            UUID id, TypeSectionDossierTravail type, String libelle, long nombrePieces) {}

    private Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }
}
