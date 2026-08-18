package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.PlanActionsRepository;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanActionsServiceTest {

    @Mock private PlanActionsRepository planActionsRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private ParametreDelaiService parametreDelaiService;

    @InjectMocks
    private PlanActionsService service;

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
                .reportSubmittedAt(Instant.now().minusSeconds(5L * 24 * 3600))
                .build();
    }

    @Test
    void creer_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Direction Générale des Impôts").contenu("Plan d'action détaillé").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiPlanDejaExistant() {
        PlanActions existant = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("DGI")
                .contenu("Plan existant")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(existant));

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Autre entité").contenu("Autre plan").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Direction Générale des Impôts").contenu("Plan d'action détaillé").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void creer_succeedsEtRenseigneSubmittedAtEtReceivedBy() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(planActionsRepository.save(any(PlanActions.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsRequest request = PlanActionsRequest.builder()
                .entiteControlee("Direction Générale des Impôts").contenu("Plan d'action détaillé").build();

        PlanActionsStatusResponse result = service.creer(investigationId, request);

        assertThat(result.isExists()).isTrue();
        assertThat(result.getEntiteControlee()).isEqualTo("Direction Générale des Impôts");
        assertThat(result.getSubmittedAt()).isNotNull();
        assertThat(result.getReceivedByNom()).isEqualTo("Jean Ouedraogo");
    }

    @Test
    void getStatus_existsFauxSiAucunPlan() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isExists()).isFalse();
        assertThat(result.getAvancements()).isEmpty();
    }

    @Test
    void getStatus_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isExists()).isFalse();
        assertThat(result.isPlanActionsOverdue()).isFalse();
        assertThat(result.getAvancements()).isEmpty();
        verify(planActionsRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getStatus_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getStatus(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).findByInvestigationId(any());
    }

    @Test
    void ajouterAvancement_rejetteSiAucunPlan() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        NoteAvancementRequest request = NoteAvancementRequest.builder().contenu("Suivi").build();

        assertThatThrownBy(() -> service.ajouterAvancement(investigationId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ajouterAvancement_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        NoteAvancementRequest request = NoteAvancementRequest.builder().contenu("Suivi").build();

        assertThatThrownBy(() -> service.ajouterAvancement(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(planActionsRepository, never()).save(any());
    }

    @Test
    void ajouterAvancement_ajouteALaListeExistante() {
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("DGI")
                .contenu("Plan d'action détaillé")
                .submittedAt(Instant.now())
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(planActions));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(planActionsRepository.save(any(PlanActions.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        NoteAvancementRequest request = NoteAvancementRequest.builder().contenu("Première étape réalisée").build();

        PlanActionsStatusResponse result = service.ajouterAvancement(investigationId, request);

        assertThat(result.getAvancements()).hasSize(1);
        assertThat(result.getAvancements().get(0).getContenu()).isEqualTo("Première étape réalisée");
        assertThat(result.getAvancements().get(0).getAgentNom()).isEqualTo("Awa Sawadogo");
    }

    @Test
    void planActionsOverdue_vraiSiEcheanceDepasseeEtAucunPlanDepose() {
        investigation.setReportSubmittedAt(Instant.now().minusSeconds(30L * 24 * 3600));
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isPlanActionsOverdue()).isTrue();
    }

    @Test
    void planActionsOverdue_fauxSiPlanDejaDepose() {
        investigation.setReportSubmittedAt(Instant.now().minusSeconds(30L * 24 * 3600));
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("DGI")
                .contenu("Plan déposé tardivement")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(planActions));
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE")).thenReturn(20);

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.isPlanActionsOverdue()).isFalse();
    }

    @Test
    void planActionsOverdue_degradeVersFauxSiParametreIndisponible() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(parametreDelaiService.resolveDelaiJours("PLAN_ACTIONS_ENTITE_CONTROLEE"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        PlanActionsStatusResponse result = service.getStatus(investigationId);

        assertThat(result.getPlanActionsDueAt()).isNull();
        assertThat(result.isPlanActionsOverdue()).isFalse();
    }
}
