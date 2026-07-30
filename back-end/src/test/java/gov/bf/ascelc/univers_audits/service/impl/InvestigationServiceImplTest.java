package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
                .agentId(agent.getId()).teamRole(TeamRole.MEMBER).build();

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
                .teamRole(TeamRole.MEMBER).active(true).build();
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
                .teamRole(TeamRole.MEMBER).active(false).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigation.getId(), agent.getId())).thenReturn(false);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
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
                .agentId(agent.getId()).teamRole(TeamRole.MEMBER).build();

        service.addMember(investigation.getId(), request, "127.0.0.1");

        assertThat(existingInactiveMember.getActive()).isTrue();
        verify(habilitationService).grant(dossier, agent, HabilitationSource.INVESTIGATION_TEAM,
                currentAgent, "Membre de l'équipe d'investigation");
    }
}
