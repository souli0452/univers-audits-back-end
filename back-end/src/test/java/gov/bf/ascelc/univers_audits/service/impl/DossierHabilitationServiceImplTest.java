package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.mapper.DossierHabilitationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
class DossierHabilitationServiceImplTest {

    @Mock private DossierHabilitationRepository habilitationRepository;
    @Mock private AgentRepository               agentRepository;
    @Mock private DossierAccessGuard            accessGuard;
    @Mock private AgentContextResolver          agentContextResolver;
    @Mock private DossierHabilitationMapper     mapper;

    @InjectMocks
    private DossierHabilitationServiceImpl service;

    @Test
    void grant_createsRowWhenNoneActiveForSource() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();
        Agent   grantedBy = Agent.builder().id(UUID.randomUUID()).build();

        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossier.getId(), agent.getId(), HabilitationSource.AGENT_IN_CHARGE))
                .thenReturn(false);

        service.grant(dossier, agent, HabilitationSource.AGENT_IN_CHARGE, grantedBy, "motif");

        verify(habilitationRepository).save(argThat(h ->
                h.getDossier() == dossier
                        && h.getAgent() == agent
                        && h.getSource() == HabilitationSource.AGENT_IN_CHARGE
                        && h.getGrantedBy() == grantedBy
                        && "motif".equals(h.getReason())));
    }

    @Test
    void grant_isNoOpWhenAlreadyActiveForSource() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();

        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossier.getId(), agent.getId(), HabilitationSource.INVESTIGATION_TEAM))
                .thenReturn(true);

        service.grant(dossier, agent, HabilitationSource.INVESTIGATION_TEAM, agent, "motif");

        verify(habilitationRepository, never()).save(any());
    }

    @Test
    void revokeBySource_revokesTheActiveRow() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();
        DossierHabilitation existing = DossierHabilitation.builder()
                .dossier(dossier).agent(agent)
                .source(HabilitationSource.INVESTIGATION_TEAM)
                .build();

        when(habilitationRepository
                .findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
                        dossier.getId(), agent.getId(), HabilitationSource.INVESTIGATION_TEAM))
                .thenReturn(Optional.of(existing));

        service.revokeBySource(dossier, agent, HabilitationSource.INVESTIGATION_TEAM);

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(habilitationRepository).save(existing);
    }

    @Test
    void revokeBySource_isNoOpWhenNoneActive() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent   agent   = Agent.builder().id(UUID.randomUUID()).build();

        when(habilitationRepository
                .findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
                        dossier.getId(), agent.getId(), HabilitationSource.INVESTIGATION_TEAM))
                .thenReturn(Optional.empty());

        service.revokeBySource(dossier, agent, HabilitationSource.INVESTIGATION_TEAM);

        verify(habilitationRepository, never()).save(any());
    }

    @Test
    void grantManual_rejectsWhenAgentAlreadyHasActiveManualGrant() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossierId, agentId, HabilitationSource.MANUAL)).thenReturn(true);

        HabilitationGrantRequest request = HabilitationGrantRequest.builder()
                .agentId(agentId).reason("Consultation étude d'opportunité").build();

        assertThatThrownBy(() -> service.grantManual(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(habilitationRepository, never()).save(any());
    }

    @Test
    void grantManual_createsRowAndReturnsResponse() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();
        Agent   currentAgent = Agent.builder().id(UUID.randomUUID()).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                dossierId, agentId, HabilitationSource.MANUAL)).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);
        when(habilitationRepository.save(any(DossierHabilitation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DossierHabilitation.class)))
                .thenReturn(DossierHabilitationResponse.builder().build());

        HabilitationGrantRequest request = HabilitationGrantRequest.builder()
                .agentId(agentId).reason("Consultation étude d'opportunité").build();

        service.grantManual(dossierId, request);

        verify(habilitationRepository).save(argThat(h ->
                h.getSource() == HabilitationSource.MANUAL
                        && h.getGrantedBy() == currentAgent
                        && "Consultation étude d'opportunité".equals(h.getReason())));
    }

    @Test
    void revokeManual_revokesAllActiveRowsForAgent() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   currentAgent = Agent.builder().id(UUID.randomUUID()).build();

        DossierHabilitation row1 = DossierHabilitation.builder()
                .source(HabilitationSource.AGENT_IN_CHARGE).build();
        DossierHabilitation row2 = DossierHabilitation.builder()
                .source(HabilitationSource.MANUAL).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(habilitationRepository.findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(List.of(row1, row2));
        when(agentContextResolver.getCurrentAgent()).thenReturn(currentAgent);

        service.revokeManual(dossierId, agentId, "Fin de mission");

        assertThat(row1.getRevokedAt()).isNotNull();
        assertThat(row2.getRevokedAt()).isNotNull();
        assertThat(row1.getRevocationReason()).isEqualTo("Fin de mission");
        verify(habilitationRepository).saveAll(List.of(row1, row2));
    }

    @Test
    void revokeManual_throwsWhenNoActiveHabilitation() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(habilitationRepository.findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.revokeManual(dossierId, agentId, "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void grantManual_throwsWhenAgentNotFound() {
        UUID dossierId = UUID.randomUUID();
        UUID agentId   = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        HabilitationGrantRequest request = HabilitationGrantRequest.builder()
                .agentId(agentId).reason("motif").build();

        assertThatThrownBy(() -> service.grantManual(dossierId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
