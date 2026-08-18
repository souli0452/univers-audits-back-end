package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.MissionSuiviRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionSuiviServiceTest {

    @Mock private MissionSuiviRepository missionSuiviRepository;
    @Mock private PlanActionsRepository planActionsRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private ParametreDelaiService parametreDelaiService;

    @InjectMocks
    private MissionSuiviService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;
    private PlanActions planActions;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .build();
        planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee("Direction Générale des Impôts")
                .contenu("Plan d'action détaillé")
                .submittedAt(Instant.now().minusSeconds(100L * 24 * 3600))
                .build();
    }

    private MissionSuiviRequest.MissionSuiviRequestBuilder validRequest() {
        return MissionSuiviRequest.builder()
                .missionDate(Instant.now())
                .objectifs("Vérifier l'application des recommandations")
                .syntheseRecommandations("3 sur 5 recommandations appliquées, retard justifié par un manque de budget");
    }

    @Test
    void ajouter_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).save(any());
    }

    @Test
    void ajouter_rejetteSiAucunPlanActions() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).save(any());
    }

    @Test
    void ajouter_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.ajouter(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).save(any());
    }

    @Test
    void ajouter_succeedsEtRenseigneConductedByEtSubmittedAt() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(missionSuiviRepository.save(any(MissionSuivi.class))).thenAnswer(inv -> inv.getArgument(0));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getInvestigationId()).isEqualTo(investigationId);
        verify(missionSuiviRepository).save(argThat(m ->
                m.getInvestigation() == investigation
                        && m.getConductedBy() == agent
                        && m.getSubmittedAt() != null
                        && m.getObjectifs().equals("Vérifier l'application des recommandations")));
    }

    @Test
    void ajouter_permetPlusieursMissionsPourLaMemeInvestigation() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        MissionSuivi missionExistante = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(Instant.now().minusSeconds(200L * 24 * 3600))
                .conductedBy(agent)
                .objectifs("Première mission")
                .syntheseRecommandations("Synthèse initiale")
                .submittedAt(Instant.now().minusSeconds(200L * 24 * 3600))
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(missionSuiviRepository.save(any(MissionSuivi.class))).thenAnswer(inv -> inv.getArgument(0));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of(missionExistante));
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.ajouter(investigationId, validRequest().build());

        assertThat(result.getMissions()).hasSize(1);
        verify(missionSuiviRepository).save(any(MissionSuivi.class));
    }

    @Test
    void lister_listeVideSiAucuneMission() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissions()).isEmpty();
    }

    @Test
    void lister_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissions()).isEmpty();
        assertThat(result.isMissionSuiviOverdue()).isFalse();
        verify(planActionsRepository, never()).findByInvestigationId(any());
        verify(missionSuiviRepository, never()).findByInvestigationIdOrderByMissionDateDesc(any());
    }

    @Test
    void lister_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.lister(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(missionSuiviRepository, never()).findByInvestigationIdOrderByMissionDateDesc(any());
    }

    @Test
    void lister_dueAtNullSiAucunPlanActions() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissionSuiviDueAt()).isNull();
        assertThat(result.isMissionSuiviOverdue()).isFalse();
    }

    @Test
    void missionSuiviOverdue_vraiSiEcheanceDepasseeEtAucuneMission() {
        planActions.setSubmittedAt(Instant.now().minusSeconds(400L * 24 * 3600));
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.isMissionSuiviOverdue()).isTrue();
    }

    @Test
    void missionSuiviOverdue_fauxSiAuMoinsUneMissionExiste() {
        planActions.setSubmittedAt(Instant.now().minusSeconds(400L * 24 * 3600));
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        MissionSuivi mission = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(Instant.now())
                .conductedBy(agent)
                .objectifs("Vérification tardive")
                .syntheseRecommandations("Synthèse")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of(mission));
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS")).thenReturn(365);

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.isMissionSuiviOverdue()).isFalse();
    }

    @Test
    void missionSuiviOverdue_degradeVersFauxSiParametreIndisponible() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(planActionsRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(planActions));
        when(missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigationId))
                .thenReturn(List.of());
        when(parametreDelaiService.resolveDelaiJours("MISSION_SUIVI_PLAN_ACTIONS"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        MissionSuiviListResponse result = service.lister(investigationId);

        assertThat(result.getMissionSuiviDueAt()).isNull();
        assertThat(result.isMissionSuiviOverdue()).isFalse();
    }
}
