package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionDossierTravailServiceTest {

    @Mock
    private SectionDossierTravailRepository sectionRepository;
    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private AttachmentRepository attachmentRepository;

    @InjectMocks
    private SectionDossierTravailService service;

    private Dossier dossier;
    private UUID dossierId;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dossier = Dossier.builder().build();
        dossier.setId(dossierId);
    }

    @Test
    void creerSectionsFixes_creeLesTroisSectionsFixes() {
        when(sectionRepository.existsByDossierIdAndType(eq(dossierId), any())).thenReturn(false);

        service.creerSectionsFixes(dossier);

        verify(sectionRepository, times(3)).save(any(SectionDossierTravail.class));
        verify(sectionRepository).existsByDossierIdAndType(
                dossierId, TypeSectionDossierTravail.ADMINISTRATION_MISSION);
        verify(sectionRepository).existsByDossierIdAndType(
                dossierId, TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENTITE);
        verify(sectionRepository).existsByDossierIdAndType(
                dossierId, TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENVIRONNEMENT);
    }

    @Test
    void creerSectionsFixes_appelDeuxiemeFoisNeCreeAucunDoublon() {
        when(sectionRepository.existsByDossierIdAndType(eq(dossierId), any())).thenReturn(true);

        service.creerSectionsFixes(dossier);

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void definirOrganisationDetail_reussitQuandNonEncoreDefini() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        service.definirOrganisationDetail(dossierId, OrganisationDetail.PAR_SITE);

        assertThat(dossier.getOrganisationDetail()).isEqualTo(OrganisationDetail.PAR_SITE);
        verify(dossierRepository).save(dossier);
    }

    @Test
    void definirOrganisationDetail_rejetteSiDejaDefini() {
        dossier.setOrganisationDetail(OrganisationDetail.PAR_ETAPE);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() ->
                service.definirOrganisationDetail(dossierId, OrganisationDetail.PAR_SITE))
                .isInstanceOf(BusinessException.class);
        verify(dossierRepository, never()).save(any());
    }

    @Test
    void creerSectionDetail_rejetteSiOrganisationNonDefinie() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.creerSectionDetail(dossierId, "Site A"))
                .isInstanceOf(BusinessException.class);
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void creerSectionDetail_reussitUneFoisOrganisationDefinie() {
        dossier.setOrganisationDetail(OrganisationDetail.PAR_SITE);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.existsByDossierIdAndTypeAndLibelle(
                dossierId, TypeSectionDossierTravail.DETAIL, "Site A")).thenReturn(false);
        when(sectionRepository.save(any(SectionDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SectionDossierTravail result = service.creerSectionDetail(dossierId, "Site A");

        assertThat(result.getType()).isEqualTo(TypeSectionDossierTravail.DETAIL);
        assertThat(result.getLibelle()).isEqualTo("Site A");
    }

    @Test
    void creerSectionDetail_rejetteLibelleDejaUtilise() {
        dossier.setOrganisationDetail(OrganisationDetail.PAR_SITE);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.existsByDossierIdAndTypeAndLibelle(
                dossierId, TypeSectionDossierTravail.DETAIL, "Site A")).thenReturn(true);

        assertThatThrownBy(() -> service.creerSectionDetail(dossierId, "Site A"))
                .isInstanceOf(BusinessException.class);
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void listerSections_retourneLesSectionsFixesMemeVides() {
        SectionDossierTravail section = SectionDossierTravail.builder()
                .dossier(dossier)
                .type(TypeSectionDossierTravail.ADMINISTRATION_MISSION)
                .build();
        section.setId(UUID.randomUUID());
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.findByDossierId(dossierId)).thenReturn(List.of(section));
        when(attachmentRepository.countBySectionId(section.getId())).thenReturn(0L);

        List<SectionDossierTravailService.SectionDossierTravailResponse> result =
                service.listerSections(dossierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombrePieces()).isZero();
    }

    @Test
    void listerSections_retourneLeCompteurDePiecesCorrect() {
        SectionDossierTravail section = SectionDossierTravail.builder()
                .dossier(dossier)
                .type(TypeSectionDossierTravail.ADMINISTRATION_MISSION)
                .build();
        section.setId(UUID.randomUUID());
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.findByDossierId(dossierId)).thenReturn(List.of(section));
        when(attachmentRepository.countBySectionId(section.getId())).thenReturn(3L);

        List<SectionDossierTravailService.SectionDossierTravailResponse> result =
                service.listerSections(dossierId);

        assertThat(result.get(0).nombrePieces()).isEqualTo(3L);
    }

    @Test
    void listerSections_rejetteSiDossierIntrouvable() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listerSections(dossierId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
