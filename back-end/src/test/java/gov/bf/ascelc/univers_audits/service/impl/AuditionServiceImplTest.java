package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
class AuditionServiceImplTest {

    @Mock private AuditionRepository auditionRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private TargetedPartyRepository targetedPartyRepository;
    @Mock private WitnessRepository witnessRepository;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private AuditionServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .build();
    }

    @Test
    void schedule_createsAuditionForWitness() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Witness witness = Witness.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(witnessRepository.findById(witness.getId()))
                .thenReturn(Optional.of(witness));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(witness.getId())
                .scheduledAt(Instant.now())
                .location("Bureau BRPD")
                .build();

        service.schedule(investigation.getId(), request);

        verify(auditionRepository).save(argThat(a ->
                a.getIntervieweeType() == IntervieweeType.WITNESS
                        && a.getWitness() == witness
                        && a.getStatus() == AuditionStatus.SCHEDULED
                        && a.getConductedBy() == agent));
    }

    @Test
    void schedule_throwsWhenBothTargetedPartyAndWitnessProvided() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(UUID.randomUUID())
                .targetedPartyId(UUID.randomUUID())
                .scheduledAt(Instant.now())
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void conduct_setsStatusAndSummary() {
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(audition.getStatus()).isEqualTo(AuditionStatus.CONDUCTED);
        assertThat(audition.getSummary()).isEqualTo("Compte-rendu");
        assertThat(audition.getConductedAt()).isNotNull();
    }

    @Test
    void conduct_throwsWhenAuditionNotScheduled() {
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CANCELLED)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByInvestigationId_returnsEmptyWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder()
                .id(UUID.randomUUID())
                .isConfidential(true)
                .build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<AuditionResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).isEmpty();
        verify(auditionRepository, never()).findByInvestigationIdOrderByScheduledAtAsc(any());
    }

    @Test
    void findByInvestigationId_throwsWhenInvestigationUnknown() {
        UUID id = UUID.randomUUID();
        when(investigationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByInvestigationId(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
