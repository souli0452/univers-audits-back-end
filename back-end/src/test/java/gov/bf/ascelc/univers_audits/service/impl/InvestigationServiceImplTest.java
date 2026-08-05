package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.EngagementConfidentialiteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EngagementConfidentialite;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import gov.bf.ascelc.univers_audits.model.entity.Mandat;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
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

    @InjectMocks
    private InvestigationServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
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
}
