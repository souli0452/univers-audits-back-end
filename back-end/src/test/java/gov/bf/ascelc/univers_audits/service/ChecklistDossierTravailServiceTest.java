package gov.bf.ascelc.univers_audits.service;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChecklistDossierTravailServiceTest {

    @Mock private InvestigationRepository investigationRepository;
    @Mock private PointChecklistDossierTravailRepository pointRepository;
    @Mock private ChecklistDossierTravailCocheRepository cocheRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private ChecklistDossierTravailService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder().id(investigationId).dossier(dossier).build();
    }

    @Test
    void getChecklist_fusionneReferentielEtEtatExistant() {
        PointChecklistDossierTravail point1 = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-01").libelle("Point 1").ordre(1).build();
        PointChecklistDossierTravail point2 = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-02").libelle("Point 2").ordre(2).build();
        ChecklistDossierTravailCoche etatPoint1 = ChecklistDossierTravailCoche.builder()
                .point(point1).coche(true).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(point1, point2));
        when(cocheRepository.findByInvestigationId(investigationId)).thenReturn(List.of(etatPoint1));

        List<ChecklistDossierTravailItemResponse> result = service.getChecklist(investigationId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("PT-01");
        assertThat(result.get(0).isCoche()).isTrue();
        assertThat(result.get(1).getCode()).isEqualTo("PT-02");
        assertThat(result.get(1).isCoche()).isFalse();
    }

    @Test
    void getChecklist_leveSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getChecklist(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(pointRepository, never()).findByActifTrueOrderByOrdreAsc();
    }

    @Test
    void getChecklist_renvoieListeVideSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<ChecklistDossierTravailItemResponse> result = service.getChecklist(investigationId);

        assertThat(result).isEmpty();
        verify(pointRepository, never()).findByActifTrueOrderByOrdreAsc();
    }

    @Test
    void setCoche_creeUneNouvelleLigneEtRenseigneCocheParEtCocheAt() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-01").libelle("Point 1").ordre(1).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByCode("PT-01")).thenReturn(Optional.of(point));
        when(cocheRepository.findByInvestigationIdAndPointId(investigationId, point.getId()))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(cocheRepository.save(any(ChecklistDossierTravailCoche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChecklistCocheRequest request = ChecklistCocheRequest.builder().coche(true).build();
        ChecklistDossierTravailItemResponse result = service.setCoche(investigationId, "PT-01", request);

        assertThat(result.isCoche()).isTrue();
        assertThat(result.getCocheAt()).isNotNull();
    }

    @Test
    void setCoche_decocherRemetCocheParEtCocheAtANull() {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .id(UUID.randomUUID()).code("PT-01").libelle("Point 1").ordre(1).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        ChecklistDossierTravailCoche existant = ChecklistDossierTravailCoche.builder()
                .investigation(investigation).point(point).coche(true)
                .cochePar(agent).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByCode("PT-01")).thenReturn(Optional.of(point));
        when(cocheRepository.findByInvestigationIdAndPointId(investigationId, point.getId()))
                .thenReturn(Optional.of(existant));
        when(cocheRepository.save(any(ChecklistDossierTravailCoche.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChecklistCocheRequest request = ChecklistCocheRequest.builder().coche(false).build();
        ChecklistDossierTravailItemResponse result = service.setCoche(investigationId, "PT-01", request);

        assertThat(result.isCoche()).isFalse();
        assertThat(result.getCocheAt()).isNull();
        assertThat(result.getCocheParNom()).isNull();
        verify(agentContextResolver, never()).getCurrentAgent();
    }

    @Test
    void setCoche_leveResourceNotFoundExceptionSiCodePointInconnu() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(pointRepository.findByCode("PT-99")).thenReturn(Optional.empty());

        ChecklistCocheRequest request = ChecklistCocheRequest.builder().coche(true).build();

        assertThatThrownBy(() -> service.setCoche(investigationId, "PT-99", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cocheRepository, never()).save(any());
    }

    @Test
    void isComplete_retourneVraiSiAucunPointActif() {
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of());

        assertThat(service.isComplete(investigationId)).isTrue();
    }

    @Test
    void isComplete_retourneFauxSiUnPointActifNestPasCoche() {
        PointChecklistDossierTravail point1 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        PointChecklistDossierTravail point2 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(point1, point2));
        when(cocheRepository.countByInvestigationIdAndCocheTrue(investigationId)).thenReturn(1L);

        assertThat(service.isComplete(investigationId)).isFalse();
    }

    @Test
    void isComplete_retourneVraiSiTousLesPointsActifsSontCoches() {
        PointChecklistDossierTravail point1 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        PointChecklistDossierTravail point2 = PointChecklistDossierTravail.builder().id(UUID.randomUUID()).build();
        when(pointRepository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(point1, point2));
        when(cocheRepository.countByInvestigationIdAndCocheTrue(investigationId)).thenReturn(2L);

        assertThat(service.isComplete(investigationId)).isTrue();
    }
}
