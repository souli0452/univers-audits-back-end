package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.SuiviProcedurePenaleRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuiviProcedurePenaleServiceTest {

    @Mock private SuiviProcedurePenaleRepository suiviProcedurePenaleRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private SuiviProcedurePenaleService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .build();
    }

    private SuiviProcedurePenaleRequest.SuiviProcedurePenaleRequestBuilder validRequest() {
        return SuiviProcedurePenaleRequest.builder()
                .phaseAt(Instant.now())
                .phase("Instruction ouverte")
                .commentaire("Dossier transmis au juge d'instruction");
    }

    @Test
    void ajouter_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(suiviProcedurePenaleRepository, never()).save(any());
    }

    @Test
    void ajouter_succeedsSurDossierConfidentielSiAgentHabilite() {
        dossier.setIsConfidential(true);
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        SuiviProcedurePenaleListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getInvestigationId()).isEqualTo(investigationId);
        verify(suiviProcedurePenaleRepository).save(any(SuiviProcedurePenale.class));
        verify(accessGuard, never()).canSeeConfidential();
    }

    @Test
    void ajouter_succeedsEtRenseigneAgentEtSubmittedAt() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        service.ajouter(investigationId, validRequest().build());

        verify(suiviProcedurePenaleRepository).save(argThat(s ->
                s.getInvestigation() == investigation
                        && s.getAgent() == agent
                        && s.getSubmittedAt() != null
                        && s.getPhase().equals("Instruction ouverte")));
    }

    @Test
    void ajouter_permetPlusieursEntreesPourLaMemeInvestigation() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        SuiviProcedurePenale entreeExistante = SuiviProcedurePenale.builder()
                .investigation(investigation)
                .phaseAt(Instant.now().minusSeconds(30L * 24 * 3600))
                .phase("Requête déposée")
                .agent(agent)
                .submittedAt(Instant.now().minusSeconds(30L * 24 * 3600))
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of(entreeExistante));

        SuiviProcedurePenaleListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getSuivis()).hasSize(1);
        verify(suiviProcedurePenaleRepository).save(argThat(s -> s.getInvestigation() == investigation));
    }

    @Test
    void ajouter_neDependPasDeRequeteParquet() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(suiviProcedurePenaleRepository.save(any(SuiviProcedurePenale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        SuiviProcedurePenaleListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result).isNotNull();
        verify(suiviProcedurePenaleRepository).save(any(SuiviProcedurePenale.class));
    }

    @Test
    void lister_listeVideSiAucuneEntree() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigationId))
                .thenReturn(List.of());

        SuiviProcedurePenaleListResponse result = service.lister(investigationId);

        assertThat(result.getSuivis()).isEmpty();
    }

    @Test
    void lister_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        SuiviProcedurePenaleListResponse result = service.lister(investigationId);

        assertThat(result.getSuivis()).isEmpty();
        verify(suiviProcedurePenaleRepository, never()).findByInvestigationIdOrderByPhaseAtDesc(any());
    }

    @Test
    void lister_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.lister(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(suiviProcedurePenaleRepository, never()).findByInvestigationIdOrderByPhaseAtDesc(any());
    }
}
