package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.EngagementConfidentialiteRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationSubmitRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationRevisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
import gov.bf.ascelc.univers_audits.model.dto.request.IncidentObjectiviteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EngagementConfidentialite;
import gov.bf.ascelc.univers_audits.model.entity.IncidentObjectivite;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import gov.bf.ascelc.univers_audits.model.entity.Mandat;
import gov.bf.ascelc.univers_audits.model.entity.PlanInvestigation;
import gov.bf.ascelc.univers_audits.model.entity.RevisionPlan;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationUpdateRequest;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceDecisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.MesureConservatoireRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ProcedureUrgenceResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MesureConservatoireResponse;
import gov.bf.ascelc.univers_audits.model.entity.ProcedureUrgence;
import gov.bf.ascelc.univers_audits.model.entity.MesureConservatoire;
import gov.bf.ascelc.univers_audits.repository.ProcedureUrgenceRepository;
import gov.bf.ascelc.univers_audits.repository.MesureConservatoireRepository;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService;
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestigationServiceImplTest {

    @Mock private InvestigationRepository       investigationRepository;
    @Mock private InvestigationMemberRepository memberRepository;
    @Mock private DossierRepository             dossierRepository;
    @Mock private AgentRepository               agentRepository;
    @Mock private NotificationRepository        notificationRepository;
    @Mock private EmailService                  emailService;
    @Mock private InvestigationMapper           investigationMapper;
    @Mock private SecurityUtils                 securityUtils;
    @Mock private AgentContextResolver          agentContextResolver;
    @Mock private DossierAuditRecorder          auditRecorder;
    @Mock private ParametreDelaiService         parametreDelaiService;
    @Mock private DossierHabilitationService    habilitationService;
    @Mock private PortalConfigService           portalConfigService;
    @Mock private MandatRepository               mandatRepository;
    @Mock private EngagementConfidentialiteRepository engagementConfidentialiteRepository;
    @Mock private PlanInvestigationRepository     planInvestigationRepository;
    @Mock private RevisionPlanRepository          revisionPlanRepository;
    @Mock private IncidentObjectiviteRepository   incidentObjectiviteRepository;
    @Mock private DossierAccessGuard              accessGuard;
    @Mock private ProcedureUrgenceRepository      procedureUrgenceRepository;
    @Mock private MesureConservatoireRepository   mesureConservatoireRepository;
    @Mock private SectionDossierTravailService sectionDossierTravailService;
    @Mock private RapportEnqueteRepository       rapportEnqueteRepository;
    @Mock private NoteRecommandationsRepository   noteRecommandationsRepository;
    @Mock private ChecklistDossierTravailService checklistDossierTravailService;

    @InjectMocks
    private InvestigationServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
    }

    private Investigation buildInProgressInvestigation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setStatus(InvestigationStatus.IN_PROGRESS);
        return investigation;
    }

    private Investigation buildCompletedInvestigation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setStatus(InvestigationStatus.COMPLETED);
        return investigation;
    }

    private RapportEnquete buildRapportComplet(Investigation investigation) {
        return RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .titre("Titre").introduction("Introduction").methodologie("Methodologie")
                .informationsCollectees("Infos").exposeFactuelAnomalies("Anomalies")
                .quantificationPrejudice("Prejudice").conclusions("Conclusions")
                .build();
    }

    @Test
    void submitReport_rejetteSiAucunRapportRedige() {
        Investigation investigation = buildInProgressInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_rejetteSiRapportIncomplet() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportIncomplet = RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .titre("Titre")
                .build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportIncomplet));

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_rejetteSiNoteRecommandationsVide() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        NoteRecommandations noteVide = NoteRecommandations.builder()
                .rapportEnquete(rapportComplet)
                .contenu("   ")
                .build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.of(noteVide));

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La note de recommandations est vide.");
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_rejetteSiAucuneNoteRedigee() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.empty());

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_rejetteSiChecklistIncomplete() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        NoteRecommandations noteComplete = NoteRecommandations.builder()
                .rapportEnquete(rapportComplet)
                .contenu("Recommandation n°1")
                .build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.of(noteComplete));
        when(checklistDossierTravailService.isComplete(investigation.getId())).thenReturn(false);

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        assertThatThrownBy(() -> service.submitReport(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void submitReport_succeedsAvecRapportEtNoteComplets() {
        Investigation investigation = buildInProgressInvestigation();
        RapportEnquete rapportComplet = buildRapportComplet(investigation);
        NoteRecommandations noteComplete = NoteRecommandations.builder()
                .rapportEnquete(rapportComplet)
                .contenu("Recommandation n°1")
                .build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(rapportComplet));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapportComplet.getId()))
                .thenReturn(Optional.of(noteComplete));
        when(checklistDossierTravailService.isComplete(investigation.getId())).thenReturn(true);
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(any(Investigation.class)))
                .thenReturn(InvestigationResponse.builder().build());

        InvestigationUpdateRequest request = InvestigationUpdateRequest.builder()
                .outcome(InvestigationOutcome.ARCHIVED).build();

        service.submitReport(investigation.getId(), request, "127.0.0.1");

        assertThat(investigation.getStatus()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(investigation.getOutcome()).isEqualTo(InvestigationOutcome.ARCHIVED);
        verify(dossierRepository).save(investigation.getDossier());
    }

    @Test
    void approveLegalAdvisor_rejetteSiStatusNestPasCompleted() {
        Investigation investigation = buildInProgressInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveLegalAdvisor(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveLegalAdvisor_succeeds() {
        Investigation investigation = buildCompletedInvestigation();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveLegalAdvisor(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getLegalAdvisorApprovedAt()).isNotNull();
        assertThat(investigation.getLegalAdvisorApprovedBy()).isEqualTo(agent);
    }

    @Test
    void approveDei_rejetteSiConseillerJuridiqueNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveDei(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveDei_succeedsApresApprobationCj() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveDei(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getDeiApprovedAt()).isNotNull();
        assertThat(investigation.getDeiApprovedBy()).isEqualTo(agent);
    }

    @Test
    void approveCgea_rejetteSiDeiNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveCgea(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveCgea_succeedsApresApprobationDei() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveCgea(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getCgeaApprovedAt()).isNotNull();
        assertThat(investigation.getCgeaApprovedBy()).isEqualTo(agent);
    }

    @Test
    void approveCge_rejetteSiCgeaNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.approveCge(investigation.getId(), "motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void approveCge_succeedsApresApprobationCgea() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        investigation.setOutcome(InvestigationOutcome.ARCHIVED);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveCge(investigation.getId(), "Motif de clôture", "127.0.0.1");

        assertThat(investigation.getCgeApprovedAt()).isNotNull();
        assertThat(investigation.getCgeApprovedBy()).isEqualTo(agent);
        assertThat(investigation.getStatus()).isEqualTo(InvestigationStatus.ARCHIVED);
        verify(dossierRepository).save(investigation.getDossier());
    }

    @Test
    void rejectDei_rejetteSiConseillerJuridiqueNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectDei(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectDei_rejetteSiMotifVide() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectDei(investigation.getId(), "   ", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectDei_remetLegalAdvisorApprovedAtANull() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setLegalAdvisorApprovedBy(Agent.builder().id(UUID.randomUUID()).build());
        Agent deiAgent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(deiAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.rejectDei(investigation.getId(), "Preuves insuffisantes", "127.0.0.1");

        assertThat(investigation.getLegalAdvisorApprovedAt()).isNull();
        assertThat(investigation.getLegalAdvisorApprovedBy()).isNull();
    }

    @Test
    void rejectCgea_rejetteSiDeiNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCgea(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCgea_rejetteSiMotifVide() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCgea(investigation.getId(), "", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCgea_remetDeiApprovedAtANull() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setDeiApprovedBy(Agent.builder().id(UUID.randomUUID()).build());
        Agent cgeaAgent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(cgeaAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.rejectCgea(investigation.getId(), "Analyse incomplète", "127.0.0.1");

        assertThat(investigation.getDeiApprovedAt()).isNull();
        assertThat(investigation.getDeiApprovedBy()).isNull();
    }

    @Test
    void rejectCge_rejetteSiCgeaNaPasApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCge(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCge_rejetteSiMotifVide() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCge(investigation.getId(), null, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCge_remetCgeaApprovedAtANull() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        investigation.setCgeaApprovedBy(Agent.builder().id(UUID.randomUUID()).build());
        Agent cgeAgent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(cgeAgent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.rejectCge(investigation.getId(), "Éléments insuffisants pour trancher", "127.0.0.1");

        assertThat(investigation.getCgeaApprovedAt()).isNull();
        assertThat(investigation.getCgeaApprovedBy()).isNull();
    }

    @Test
    void circuitComplet_sequenceNominaleApprouveLesQuatreEtapes() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setOutcome(InvestigationOutcome.ARCHIVED);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.approveLegalAdvisor(investigation.getId(), "127.0.0.1");
        service.approveDei(investigation.getId(), "127.0.0.1");
        service.approveCgea(investigation.getId(), "127.0.0.1");
        service.approveCge(investigation.getId(), "Décision finale", "127.0.0.1");

        assertThat(investigation.getLegalAdvisorApprovedAt()).isNotNull();
        assertThat(investigation.getDeiApprovedAt()).isNotNull();
        assertThat(investigation.getCgeaApprovedAt()).isNotNull();
        assertThat(investigation.getCgeApprovedAt()).isNotNull();
        assertThat(investigation.getStatus()).isEqualTo(InvestigationStatus.ARCHIVED);
    }

    @Test
    void rejectDei_rejetteSiEtapeDeiDejaApprouvee() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectDei(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void rejectCge_rejetteSiRapportDejaDecideParLeCge() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setLegalAdvisorApprovedAt(Instant.now());
        investigation.setDeiApprovedAt(Instant.now());
        investigation.setCgeaApprovedAt(Instant.now());
        investigation.setCgeApprovedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.rejectCge(investigation.getId(), "Motif", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(investigationRepository, never()).save(any());
    }

    @Test
    void addMember_grantsInvestigationTeamHabilitation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(false).build()));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(memberRepository.findFirstByInvestigationIdAndAgentIdOrderByCreatedAtDesc(
                investigation.getId(), agent.getId())).thenReturn(Optional.empty());
        when(memberRepository.save(any(InvestigationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.INVESTIGATEUR).build();

        service.addMember(investigation.getId(), request, "127.0.0.1");

        verify(habilitationService).grant(dossier, agent, HabilitationSource.INVESTIGATION_TEAM,
                currentAgent, "Membre de l'équipe d'investigation");
    }

    @Test
    void removeMember_revokesInvestigationTeamHabilitation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-current").build();

        InvestigationMember member = InvestigationMember.builder()
                .investigation(investigation).agent(agent)
                .teamRole(TeamRole.INVESTIGATEUR).active(true).build();
        investigation.getMembers().add(member);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(memberRepository.save(any(InvestigationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        service.removeMember(investigation.getId(), agent.getId(), "127.0.0.1");

        verify(habilitationService).revokeBySource(
                dossier, agent, HabilitationSource.INVESTIGATION_TEAM, currentAgent);
    }

    /**
     * Finding 5 (spec Tests) : réactiver un membre précédemment retiré (pas
     * seulement en ajouter un tout nouveau) doit toujours déclencher l'octroi
     * INVESTIGATION_TEAM — c'est la branche "existing.isPresent()" de
     * addMember, jamais exercée par addMember_grantsInvestigationTeamHabilitation
     * (qui ne couvre que la branche "nouveau membre").
     */
    @Test
    void addMember_reactivationBranchStillGrantsInvestigationTeamHabilitation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-current").build();

        InvestigationMember existingInactiveMember = InvestigationMember.builder()
                .investigation(investigation).agent(agent)
                .teamRole(TeamRole.INVESTIGATEUR).active(false).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(false).build()));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(memberRepository.findFirstByInvestigationIdAndAgentIdOrderByCreatedAtDesc(
                investigation.getId(), agent.getId()))
                .thenReturn(Optional.of(existingInactiveMember));
        when(memberRepository.save(any(InvestigationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.INVESTIGATEUR).build();

        service.addMember(investigation.getId(), request, "127.0.0.1");

        assertThat(existingInactiveMember.getActive()).isTrue();
        verify(habilitationService).grant(dossier, agent, HabilitationSource.INVESTIGATION_TEAM,
                currentAgent, "Membre de l'équipe d'investigation");
    }

    @Test
    void start_rejectsWhenNotInitiated() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .status(gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("initiée");
    }

    @Test
    void start_rejectsWhenNoChefDeMission() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(0L);

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chef de mission");
    }

    @Test
    void start_rejectsWhenOnlyOneInvestigateur() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(1L);

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("investigateurs");
    }

    @Test
    void start_rejectsWhenNoConseilJuridique() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(0L);

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("conseil juridique");
    }

    @Test
    void start_rejectsWhenCompositionValidButNoMandat() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mandat");
    }

    @Test
    void start_rejectsWhenNoPlanExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun plan");
    }

    @Test
    void start_rejectsWhenPlanNotValidated() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(null).build()));

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("validé");
    }

    @Test
    void start_succeedsWithFullCompositionAndMandat() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setPlannedDurationDays(30);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(java.time.Instant.now()).build()));
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);

        service.start(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getStatus())
                .isEqualTo(gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS);
    }

    @Test
    void start_declencheLaCreationDesSectionsFixesDuDossierDeTravail() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setPlannedDurationDays(30);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(java.time.Instant.now()).build()));
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);

        service.start(investigation.getId(), "127.0.0.1");

        verify(sectionDossierTravailService).creerSectionsFixes(dossier);
    }

    /**
     * Finding 3 (revue finale) : démontre le comportement composé du Lot —
     * un plan validé puis révisé (via la vraie logique de revisePlan(), pas un
     * stub construit à la main) laisse start() de nouveau bloqué, preuve que le
     * garde-fou de start() lit correctement l'état réel post-révision.
     */
    @Test
    void start_rejectsAfterPlanRevisedPostValidation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setPlannedDurationDays(30);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        PlanInvestigation validatedPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .objectifs("Objectifs initiaux")
                .methodologie("Méthodologie initiale")
                .planVersion(1)
                .submittedAt(Instant.now())
                .submittedBy(currentAgent)
                .validatedAt(Instant.now())
                .validatedBy(currentAgent)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(validatedPlan));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlanInvestigationRevisionRequest revisionRequest = PlanInvestigationRevisionRequest.builder()
                .objectifs("Objectifs révisés").methodologie("Méthodologie révisée")
                .motifRevision("Ajustement du périmètre").build();

        service.revisePlan(investigation.getId(), revisionRequest, "127.0.0.1");
        assertThat(validatedPlan.getValidatedAt()).isNull();

        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(Instant.now()).build()));

        assertThatThrownBy(() -> service.start(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("validé");
    }

    @Test
    void deliverMandat_rejectsWhenAlreadyDelivered() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.deliverMandat(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été délivré");
    }

    @Test
    void deliverMandat_rejectsWhenCompositionIncomplete() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(0L);

        assertThatThrownBy(() -> service.deliverMandat(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chef de mission");
    }

    @Test
    void deliverMandat_succeedsWithFullComposition() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);
        when(mandatRepository.save(any(Mandat.class)))
                .thenAnswer(inv -> {
                    Mandat m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        MandatResponse response = service.deliverMandat(investigation.getId(), "127.0.0.1");

        assertThat(response.getAgentCGEId()).isEqualTo(cge.getId());
        assertThat(response.getInvestigationId()).isEqualTo(investigation.getId());
    }

    @Test
    void getMandat_returnsResponseWhenMandatExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent cge = Agent.builder().id(UUID.randomUUID())
                .firstName("Jean").lastName("Dupont").build();
        Instant dateDelivrance = Instant.now();
        Mandat mandat = Mandat.builder().id(UUID.randomUUID())
                .investigation(investigation).agentCGE(cge)
                .dateDelivrance(dateDelivrance).build();

        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(mandat));

        MandatResponse response = service.getMandat(investigation.getId());

        assertThat(response.getId()).isEqualTo(mandat.getId());
        assertThat(response.getInvestigationId()).isEqualTo(investigation.getId());
        assertThat(response.getDateDelivrance()).isEqualTo(dateDelivrance);
        assertThat(response.getAgentCGEId()).isEqualTo(cge.getId());
        assertThat(response.getAgentCGENom()).isEqualTo("Jean Dupont");
    }

    @Test
    void getMandat_throwsWhenNoMandat() {
        UUID investigationId = UUID.randomUUID();

        when(mandatRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMandat(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Finding 6 (revue finale) : PERSONNE_RESSOURCE est volontairement exclu de
     * validateTeamComposition — nombre libre, jamais compté ni contraint. Ce test
     * confirme que start() réussit avec la composition standard (sans jamais
     * interroger le repository pour ce rôle) et documente ce choix explicitement.
     */
    @Test
    void start_succeedsWithExtraPersonneRessource() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setPlannedDurationDays(30);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(Instant.now()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(Instant.now()).build()));
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);

        service.start(investigation.getId(), "127.0.0.1");

        assertThat(investigation.getStatus())
                .isEqualTo(gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS);
        verify(memberRepository, never()).countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.PERSONNE_RESSOURCE);
    }

    @Test
    void addMember_rejectsWhenNoEngagementDeclared() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId())).thenReturn(Optional.empty());

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.INVESTIGATEUR).build();

        assertThatThrownBy(() -> service.addMember(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("engagement");
    }

    @Test
    void addMember_rejectsWhenConflictOfInterestDeclared() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), agent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(true).build()));

        AddMemberRequest request = AddMemberRequest.builder()
                .agentId(agent.getId()).teamRole(TeamRole.INVESTIGATEUR).build();

        assertThatThrownBy(() -> service.addMember(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("conflit d'intérêts");
    }

    @Test
    void declareEngagementPrealable_succeedsWithoutConflict() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), currentAgent.getId())).thenReturn(Optional.empty());
        when(engagementConfidentialiteRepository.save(any(EngagementConfidentialite.class)))
                .thenAnswer(inv -> {
                    EngagementConfidentialite e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(false).build();

        EngagementConfidentialiteResponse response =
                service.declareEngagementPrealable(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getAgentId()).isEqualTo(currentAgent.getId());
        assertThat(response.getHasConflictOfInterest()).isFalse();
    }

    @Test
    void declareEngagementPrealable_succeedsWithConflictAndDetails() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), currentAgent.getId())).thenReturn(Optional.empty());
        when(engagementConfidentialiteRepository.save(any(EngagementConfidentialite.class)))
                .thenAnswer(inv -> {
                    EngagementConfidentialite e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(true)
                .conflictDetails("Lien familial avec le mis en cause")
                .build();

        EngagementConfidentialiteResponse response =
                service.declareEngagementPrealable(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getAgentId()).isEqualTo(currentAgent.getId());
        assertThat(response.getHasConflictOfInterest()).isTrue();
        assertThat(response.getConflictDetails())
                .isEqualTo("Lien familial avec le mis en cause");
    }

    @Test
    void declareEngagementPrealable_rejectsWhenConflictDetailsMissing() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(true).build();

        assertThatThrownBy(() -> service.declareEngagementPrealable(
                investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("préciser");
    }

    @Test
    void declareEngagementPrealable_rejectsWhenAlreadyDeclared() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigation.getId(), currentAgent.getId()))
                .thenReturn(Optional.of(EngagementConfidentialite.builder()
                        .hasConflictOfInterest(false).build()));

        EngagementConfidentialiteRequest request = EngagementConfidentialiteRequest.builder()
                .hasConflictOfInterest(false).build();

        assertThatThrownBy(() -> service.declareEngagementPrealable(
                investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été soumise");
    }

    @Test
    void getEngagementPrealable_returnsResponseWhenExists() {
        UUID investigationId = UUID.randomUUID();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(
                Dossier.builder().id(UUID.randomUUID()).build());
        EngagementConfidentialite engagement = EngagementConfidentialite.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .agent(agent)
                .hasConflictOfInterest(false)
                .signedAt(java.time.Instant.now())
                .build();

        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigationId, agent.getId())).thenReturn(Optional.of(engagement));

        EngagementConfidentialiteResponse response =
                service.getEngagementPrealable(investigationId, agent.getId());

        assertThat(response.getAgentId()).isEqualTo(agent.getId());
        assertThat(response.getHasConflictOfInterest()).isFalse();
    }

    @Test
    void getEngagementPrealable_throwsWhenNotFound() {
        UUID investigationId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        when(engagementConfidentialiteRepository.findByInvestigationIdAndAgentId(
                investigationId, agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEngagementPrealable(investigationId, agentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitPlan_rejectsWhenNoMandat() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        PlanInvestigationSubmitRequest request = PlanInvestigationSubmitRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .build();

        assertThatThrownBy(() -> service.submitPlan(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mandat");
    }

    @Test
    void submitPlan_rejectsWhenPlanAlreadyExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID()).build()));

        PlanInvestigationSubmitRequest request = PlanInvestigationSubmitRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .build();

        assertThatThrownBy(() -> service.submitPlan(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void submitPlan_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> {
                    PlanInvestigation p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        PlanInvestigationSubmitRequest request = PlanInvestigationSubmitRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .build();

        PlanInvestigationResponse response =
                service.submitPlan(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getPlanVersion()).isEqualTo(1);
        assertThat(response.getValidatedAt()).isNull();
    }

    @Test
    void revisePlan_rejectsWhenNoPlanExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        PlanInvestigationRevisionRequest request = PlanInvestigationRevisionRequest.builder()
                .objectifs("Établir les faits").methodologie("Auditions et documents")
                .motifRevision("Ajustement du périmètre").build();

        assertThatThrownBy(() -> service.revisePlan(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun plan");
    }

    @Test
    void revisePlan_succeedsAndResetsValidation() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        PlanInvestigation existingPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .objectifs("Objectifs initiaux")
                .methodologie("Méthodologie initiale")
                .planVersion(1)
                .submittedAt(Instant.now())
                .submittedBy(currentAgent)
                .validatedAt(Instant.now())
                .validatedBy(currentAgent)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(existingPlan));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlanInvestigationRevisionRequest request = PlanInvestigationRevisionRequest.builder()
                .objectifs("Objectifs révisés").methodologie("Méthodologie révisée")
                .motifRevision("Ajustement du périmètre").build();

        PlanInvestigationResponse response =
                service.revisePlan(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getPlanVersion()).isEqualTo(2);
        assertThat(response.getValidatedAt()).isNull();
        assertThat(response.getObjectifs()).isEqualTo("Objectifs révisés");
        verify(revisionPlanRepository).save(argThat(r ->
                r.getVersionNumber() == 1 && r.getObjectifs().equals("Objectifs initiaux")));
        assertThat(response.getSubmittedAt()).isEqualTo(existingPlan.getSubmittedAt());
        assertThat(response.getSubmittedById()).isEqualTo(currentAgent.getId());
    }

    @Test
    void validatePlan_rejectsWhenNoPlanExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validatePlan(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun plan");
    }

    @Test
    void validatePlan_rejectsWhenAlreadyValidated() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        PlanInvestigation existingPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .validatedAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(existingPlan));

        assertThatThrownBy(() -> service.validatePlan(investigation.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été validé");
    }

    @Test
    void validatePlan_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        PlanInvestigation existingPlan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .submittedBy(currentAgent)
                .validatedAt(null)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(existingPlan));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(planInvestigationRepository.save(any(PlanInvestigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlanInvestigationResponse response =
                service.validatePlan(investigation.getId(), "127.0.0.1");

        assertThat(response.getValidatedAt()).isNotNull();
        assertThat(response.getValidatedById()).isEqualTo(currentAgent.getId());
    }

    @Test
    void getPlan_computesOverdueFromMandatDate() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .dossier(Dossier.builder().id(UUID.randomUUID()).build())
                .build();
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).build();
        PlanInvestigation plan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .submittedBy(currentAgent)
                .validatedAt(null)
                .build();
        Mandat mandat = Mandat.builder()
                .dateDelivrance(Instant.now().minusSeconds(30L * 24 * 3600))
                .build();

        when(planInvestigationRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(plan));
        when(mandatRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(mandat));
        when(parametreDelaiService.resolveDelaiJours("VALIDATION_PLAN_INVESTIGATION_DEI"))
                .thenReturn(8);

        PlanInvestigationResponse response = service.getPlan(investigationId);

        assertThat(response.isOverdue()).isTrue();
        assertThat(response.getValidationDeadline()).isNotNull();
    }

    @Test
    void getPlan_throwsWhenNotFound() {
        UUID investigationId = UUID.randomUUID();

        when(planInvestigationRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPlanRevisions_returnsOrderedHistory() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(
                Dossier.builder().id(UUID.randomUUID()).build());
        Agent currentAgent = Agent.builder().id(UUID.randomUUID()).build();
        PlanInvestigation plan = PlanInvestigation.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .build();
        RevisionPlan revision = RevisionPlan.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .objectifs("Objectifs initiaux")
                .methodologie("Méthodologie initiale")
                .revisedAt(Instant.now())
                .revisedBy(currentAgent)
                .motifRevision("Ajustement du périmètre")
                .build();

        when(planInvestigationRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(plan));
        when(revisionPlanRepository.findByPlanInvestigationIdOrderByVersionNumberDesc(
                plan.getId())).thenReturn(List.of(revision));

        List<RevisionPlanResponse> revisions = service.getPlanRevisions(investigationId);

        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).getMotifRevision()).isEqualTo("Ajustement du périmètre");
    }

    @Test
    void declareIncident_succeedsAndChecksDossierAccess() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(incidentObjectiviteRepository.save(any(IncidentObjectivite.class)))
                .thenAnswer(inv -> {
                    IncidentObjectivite i = inv.getArgument(0);
                    i.setId(UUID.randomUUID());
                    return i;
                });
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.empty());

        IncidentObjectiviteRequest request = IncidentObjectiviteRequest.builder()
                .description("Lien personnel découvert avec une partie visée").build();

        IncidentObjectiviteResponse response =
                service.declareIncident(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getDeclaredById()).isEqualTo(currentAgent.getId());
        assertThat(response.getDescription())
                .isEqualTo("Lien personnel découvert avec une partie visée");
        verify(accessGuard).checkReadAccess(dossier);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void declareIncident_propagatesAccessGuardRejection() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        IncidentObjectiviteRequest request = IncidentObjectiviteRequest.builder()
                .description("Incident").build();

        assertThatThrownBy(() -> service.declareIncident(investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accès refusé");

        verify(incidentObjectiviteRepository, never()).save(any());
    }

    @Test
    void declareIncident_notifiesCgeWhenMandatExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        Agent cge = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-cge").build();
        Mandat mandat = Mandat.builder().agentCGE(cge).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(incidentObjectiviteRepository.save(any(IncidentObjectivite.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(mandat));

        IncidentObjectiviteRequest request = IncidentObjectiviteRequest.builder()
                .description("Incident").build();

        service.declareIncident(investigation.getId(), request, "127.0.0.1");

        verify(notificationRepository).save(argThat(n ->
                n.getRecipient().equals("kc-cge")
                        && n.getType() == NotificationType.INTERNAL_ALERT));
    }

    @Test
    void getIncidents_returnsOrderedList() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent declarant = Agent.builder().id(UUID.randomUUID()).build();
        IncidentObjectivite incident = IncidentObjectivite.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .declaredBy(declarant)
                .description("Incident")
                .declaredAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(incidentObjectiviteRepository
                .findByInvestigationIdOrderByDeclaredAtDesc(investigation.getId()))
                .thenReturn(List.of(incident));

        List<IncidentObjectiviteResponse> incidents =
                service.getIncidents(investigation.getId());

        assertThat(incidents).hasSize(1);
        assertThat(incidents.get(0).getDeclaredById()).isEqualTo(declarant.getId());
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void demanderProcedureUrgence_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(procedureUrgenceRepository.save(any(ProcedureUrgence.class)))
                .thenAnswer(inv -> {
                    ProcedureUrgence p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        ProcedureUrgenceRequest request = ProcedureUrgenceRequest.builder()
                .justification("Risque de destruction de preuves").build();

        ProcedureUrgenceResponse response =
                service.demanderProcedureUrgence(investigation.getId(), request, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(StatutProcedureUrgence.EN_ATTENTE);
        assertThat(response.getRequestedById()).isEqualTo(currentAgent.getId());
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void approuverProcedureUrgence_rejectsWhenAlreadyDecided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .status(StatutProcedureUrgence.APPROUVEE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder().build();

        assertThatThrownBy(() -> service.approuverProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été décidée");
    }

    @Test
    void approuverProcedureUrgence_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .requestedBy(currentAgent)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(procedureUrgenceRepository.save(any(ProcedureUrgence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder().build();

        ProcedureUrgenceResponse response = service.approuverProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(StatutProcedureUrgence.APPROUVEE);
        assertThat(response.getDecidedById()).isEqualTo(currentAgent.getId());
    }

    @Test
    void rejeterProcedureUrgence_rejectsWhenMotifMissing() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder().build();

        assertThatThrownBy(() -> service.rejeterProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motif du rejet");
    }

    @Test
    void rejeterProcedureUrgence_succeeds() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .requestedBy(currentAgent)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByIdAndInvestigationId(
                procedure.getId(), investigation.getId()))
                .thenReturn(Optional.of(procedure));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(procedureUrgenceRepository.save(any(ProcedureUrgence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProcedureUrgenceDecisionRequest request = ProcedureUrgenceDecisionRequest.builder()
                .motifDecision("Situation déjà maîtrisée par les moyens existants").build();

        ProcedureUrgenceResponse response = service.rejeterProcedureUrgence(
                investigation.getId(), procedure.getId(), request, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(StatutProcedureUrgence.REJETEE);
        assertThat(response.getMotifDecision())
                .isEqualTo("Situation déjà maîtrisée par les moyens existants");
    }

    @Test
    void declarerMesureConservatoire_rejectsWhenNoApprovedProcedure() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.existsByInvestigationIdAndStatus(
                investigation.getId(), StatutProcedureUrgence.APPROUVEE)).thenReturn(false);

        MesureConservatoireRequest request = MesureConservatoireRequest.builder()
                .description("Mise sous scellés du serveur").build();

        assertThatThrownBy(() -> service.declarerMesureConservatoire(
                investigation.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucune procédure d'urgence approuvée");
    }

    @Test
    void declarerMesureConservatoire_succeedsWhenApprovedProcedureExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent currentAgent = Agent.builder().id(UUID.randomUUID())
                .keycloakId("kc-current").build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.existsByInvestigationIdAndStatus(
                investigation.getId(), StatutProcedureUrgence.APPROUVEE)).thenReturn(true);
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(mesureConservatoireRepository.save(any(MesureConservatoire.class)))
                .thenAnswer(inv -> {
                    MesureConservatoire m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        MesureConservatoireRequest request = MesureConservatoireRequest.builder()
                .description("Mise sous scellés du serveur").build();

        MesureConservatoireResponse response = service.declarerMesureConservatoire(
                investigation.getId(), request, "127.0.0.1");

        assertThat(response.getTakenById()).isEqualTo(currentAgent.getId());
        assertThat(response.getDescription()).isEqualTo("Mise sous scellés du serveur");
    }

    @Test
    void getProcedures_returnsOrderedList() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        ProcedureUrgence procedure = ProcedureUrgence.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .requestedBy(Agent.builder().id(UUID.randomUUID()).build())
                .status(StatutProcedureUrgence.EN_ATTENTE)
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(procedureUrgenceRepository.findByInvestigationIdOrderByRequestedAtDesc(
                investigation.getId())).thenReturn(List.of(procedure));

        List<ProcedureUrgenceResponse> procedures = service.getProcedures(investigation.getId());

        assertThat(procedures).hasSize(1);
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void getMesures_returnsOrderedList() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        MesureConservatoire mesure = MesureConservatoire.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .takenBy(Agent.builder().id(UUID.randomUUID()).build())
                .description("Mise sous scellés")
                .takenAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(mesureConservatoireRepository.findByInvestigationIdOrderByTakenAtDesc(
                investigation.getId())).thenReturn(List.of(mesure));

        List<MesureConservatoireResponse> mesures = service.getMesures(investigation.getId());

        assertThat(mesures).hasSize(1);
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void getProcedures_returnsEmptyWhenConfidentialAndCannotSeeConfidential() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .isConfidential(true)
                .build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<ProcedureUrgenceResponse> procedures = service.getProcedures(investigation.getId());

        assertThat(procedures).isEmpty();
        verify(procedureUrgenceRepository, never())
                .findByInvestigationIdOrderByRequestedAtDesc(any());
    }

    @Test
    void getMesures_returnsEmptyWhenConfidentialAndCannotSeeConfidential() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .isConfidential(true)
                .build();
        Investigation investigation = buildInvestigation(dossier);

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<MesureConservatoireResponse> mesures = service.getMesures(investigation.getId());

        assertThat(mesures).isEmpty();
        verify(mesureConservatoireRepository, never())
                .findByInvestigationIdOrderByTakenAtDesc(any());
    }

    @Test
    void findById_calculeEcheanceCjDepuisReportSubmittedAt() {
        Investigation investigation = buildCompletedInvestigation();
        Instant submittedAt = Instant.parse("2026-01-01T00:00:00Z");
        investigation.setReportSubmittedAt(submittedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueDeadline()).isEqualTo(submittedAt.plusSeconds(10L * 24 * 3600));
    }

    @Test
    void findById_cjRevueOverdueVraiSiEcheanceDepasseeEtPasEncoreApprouve() {
        Investigation investigation = buildCompletedInvestigation();
        Instant submittedAt = Instant.now().minusSeconds(20L * 24 * 3600);
        investigation.setReportSubmittedAt(submittedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueOverdue()).isTrue();
    }

    @Test
    void findById_neCalculeAucuneEcheanceSiRapportPasEncoreSoumis() {
        Investigation investigation = buildInProgressInvestigation();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueDeadline()).isNull();
        verify(parametreDelaiService, never()).resolveDelaiJours(any());
    }

    @Test
    void findById_degradeVersNullSiParametreDelaiIndisponible() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setReportSubmittedAt(Instant.now());
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCjRevueDeadline()).isNull();
        assertThat(result.getCjRevueOverdue()).isFalse();
    }

    @Test
    void findById_calculeEcheanceDeiDepuisLegalAdvisorApprovedAtPasReportSubmittedAt() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setReportSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
        Instant cjApprovedAt = Instant.parse("2026-01-05T00:00:00Z");
        investigation.setLegalAdvisorApprovedAt(cjApprovedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);
        when(parametreDelaiService.resolveDelaiJours("ANALYSE_DEI_RAPPORT")).thenReturn(15);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getDeiAnalyseDeadline()).isEqualTo(cjApprovedAt.plusSeconds(15L * 24 * 3600));
    }

    @Test
    void findById_calculeEcheanceCgeaDepuisDeiApprovedAt() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setReportSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
        investigation.setLegalAdvisorApprovedAt(Instant.parse("2026-01-05T00:00:00Z"));
        Instant deiApprovedAt = Instant.parse("2026-01-10T00:00:00Z");
        investigation.setDeiApprovedAt(deiApprovedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);
        when(parametreDelaiService.resolveDelaiJours("ANALYSE_DEI_RAPPORT")).thenReturn(15);
        when(parametreDelaiService.resolveDelaiJours("APPROBATION_CGEA_RAPPORT")).thenReturn(10);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCgeaApprobationDeadline())
                .isEqualTo(deiApprovedAt.plusSeconds(10L * 24 * 3600));
    }

    @Test
    void findById_calculeEcheanceCgeDepuisCgeaApprovedAt() {
        Investigation investigation = buildCompletedInvestigation();
        investigation.setReportSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
        investigation.setLegalAdvisorApprovedAt(Instant.parse("2026-01-05T00:00:00Z"));
        investigation.setDeiApprovedAt(Instant.parse("2026-01-10T00:00:00Z"));
        Instant cgeaApprovedAt = Instant.parse("2026-01-15T00:00:00Z");
        investigation.setCgeaApprovedAt(cgeaApprovedAt);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.findByInvestigationIdAndActiveTrue(investigation.getId()))
                .thenReturn(List.of());
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(parametreDelaiService.resolveDelaiJours("REVUE_CJ_RAPPORT")).thenReturn(10);
        when(parametreDelaiService.resolveDelaiJours("ANALYSE_DEI_RAPPORT")).thenReturn(15);
        when(parametreDelaiService.resolveDelaiJours("APPROBATION_CGEA_RAPPORT")).thenReturn(10);
        when(parametreDelaiService.resolveDelaiJours("APPROBATION_CGE")).thenReturn(20);

        InvestigationResponse result = service.findById(investigation.getId());

        assertThat(result.getCgeApprobationDeadline())
                .isEqualTo(cgeaApprovedAt.plusSeconds(20L * 24 * 3600));
    }
}
