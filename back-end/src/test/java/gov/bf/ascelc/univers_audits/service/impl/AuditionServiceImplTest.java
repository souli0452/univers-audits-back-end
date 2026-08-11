package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
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
    @Mock private AgentRepository agentRepository;
    @Mock private DossierDetailsMapper mapper;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AuditionDisplayNameMasker displayNameMasker;

    @InjectMocks
    private AuditionServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .build();
    }

    private List<UUID> twoInvestigatorIds(Agent a1, Agent a2) {
        when(agentRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(agentRepository.findById(a2.getId())).thenReturn(Optional.of(a2));
        return List.of(a1.getId(), a2.getId());
    }

    @Test
    void schedule_createsAuditionForWitness() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Witness witness = Witness.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(witnessRepository.findById(witness.getId()))
                .thenReturn(Optional.of(witness));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of());
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(witness.getId())
                .scheduledAt(Instant.now())
                .location("Bureau BRPD")
                .investigatorIds(investigatorIds)
                .build();

        service.schedule(investigation.getId(), request);

        verify(auditionRepository).save(argThat(a ->
                a.getIntervieweeType() == IntervieweeType.WITNESS
                        && a.getWitness() == witness
                        && a.getStatus() == AuditionStatus.SCHEDULED
                        && a.getInvestigators().containsAll(List.of(agent1, agent2))
                        && a.getInvestigators().size() == 2));
    }

    @Test
    void schedule_createsAuditionForDeclarant() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID()).build();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).declarant(declarant).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of());
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        service.schedule(investigation.getId(), request);

        verify(auditionRepository).save(argThat(a ->
                a.getIntervieweeType() == IntervieweeType.DECLARANT
                        && a.getTargetedParty() == null
                        && a.getWitness() == null));
    }

    @Test
    void schedule_throwsWhenDeclarantMissingOnAnonymousDossier() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).declarant(null).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .scheduledAt(Instant.now())
                .investigatorIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
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
                .investigatorIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void schedule_throwsWhenInvestigatorIdsContainDuplicate() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        UUID sameId = UUID.randomUUID();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(UUID.randomUUID())
                .scheduledAt(Instant.now())
                .investigatorIds(List.of(sameId, sameId))
                .build();

        assertThatThrownBy(() -> service.schedule(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void schedule_appliesDisplayNameMaskerToResponse() {
        Declarant declarant = Declarant.builder().id(UUID.randomUUID()).build();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).declarant(declarant).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of());
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(any(Audition.class))).thenReturn("Nom masqué");

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.DECLARANT)
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        AuditionResponse response = service.schedule(investigation.getId(), request);

        assertThat(response.getIntervieweeDisplayName()).isEqualTo("Nom masqué");
    }

    @Test
    void schedule_setsSecondAuditionWarningForRepeatedTargetedParty() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        TargetedParty targetedParty = TargetedParty.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Audition previousAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .targetedParty(targetedParty)
                .status(AuditionStatus.CONDUCTED)
                .build();
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(targetedPartyRepository.findById(targetedParty.getId()))
                .thenReturn(Optional.of(targetedParty));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of(previousAudition));
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .targetedPartyId(targetedParty.getId())
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        AuditionResponse response = service.schedule(investigation.getId(), request);

        assertThat(response.getSecondAuditionWarning()).isNotBlank();
    }

    @Test
    void schedule_noSecondAuditionWarningForWitnessRepeatedInterview() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Witness witness = Witness.builder().id(UUID.randomUUID()).dossier(dossier).build();
        Audition previousAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(witness)
                .status(AuditionStatus.CONDUCTED)
                .build();
        Agent agent1 = Agent.builder().id(UUID.randomUUID()).build();
        Agent agent2 = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(witnessRepository.findById(witness.getId()))
                .thenReturn(Optional.of(witness));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of(previousAudition));
        List<UUID> investigatorIds = twoInvestigatorIds(agent1, agent2);
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionScheduleRequest request = AuditionScheduleRequest.builder()
                .intervieweeType(IntervieweeType.WITNESS)
                .witnessId(witness.getId())
                .scheduledAt(Instant.now())
                .investigatorIds(investigatorIds)
                .build();

        AuditionResponse response = service.schedule(investigation.getId(), request);

        assertThat(response.getSecondAuditionWarning()).isNull();
    }

    @Test
    void conduct_setsStatusAndSummary() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(audition));
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
    void conduct_appliesDisplayNameMaskerToResponse() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(any(Audition.class))).thenReturn("Nom masqué");

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(response.getIntervieweeDisplayName()).isEqualTo("Nom masqué");
    }

    @Test
    void conduct_appliesDisplayNameMaskerToOrderWarningText() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Witness pendingWitness = Witness.builder().id(UUID.randomUUID())
                .dossier(dossier).firstName("Jean").lastName("Kaboré")
                .possiblyImplicated(false).build();
        Audition pendingEarlierAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(pendingWitness)
                .status(AuditionStatus.SCHEDULED)
                .build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();

        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(pendingEarlierAudition, audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(pendingEarlierAudition)).thenReturn("Témoin masqué");
        when(displayNameMasker.mask(audition)).thenReturn("Nom masqué");

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(response.getOrderWarning()).contains("Témoin masqué");
    }

    @Test
    void conduct_throwsWhenAuditionNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CANCELLED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void conduct_setsOrderWarningWhenEarlierRankStillScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Witness pendingWitness = Witness.builder().id(UUID.randomUUID())
                .dossier(dossier).firstName("Jean").lastName("Kaboré")
                .possiblyImplicated(false).build();
        Audition pendingEarlierAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(pendingWitness)
                .status(AuditionStatus.SCHEDULED)
                .build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();

        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(pendingEarlierAudition, audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(response.getOrderWarning()).isNotBlank();
    }

    @Test
    void conduct_witnessAuditionWithoutWitnessDoesNotThrowAndRanksAsOne() {
        // Legacy/direct-DB data can leave a WITNESS audition with no witness attached
        // (witness_id is a nullable FK) even though schedule() always sets one for new
        // auditions. orderRank() must not NPE on audition.getWitness() in that case.
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition pendingWitnessLessAudition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.WITNESS)
                .witness(null)
                .status(AuditionStatus.SCHEDULED)
                .build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();

        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(pendingWitnessLessAudition, audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        // Witness-less WITNESS audition is treated as rank 1 (< TARGETED_PARTY's rank 3),
        // so it still surfaces as an order warning rather than throwing.
        assertThat(response.getOrderWarning()).isNotBlank();
    }

    @Test
    void conduct_noOrderWarningWhenOrderRespected() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.DECLARANT)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();

        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(any()))
                .thenReturn(List.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        AuditionResponse response = service.conduct(audition.getId(),
                AuditionConductRequest.builder().summary("Compte-rendu").build());

        assertThat(response.getOrderWarning()).isNull();
    }

    @Test
    void cancel_setsStatusAndReason() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        service.cancel(audition.getId(), "Personne injoignable");

        assertThat(audition.getStatus()).isEqualTo(AuditionStatus.CANCELLED);
        assertThat(audition.getCancellationReason()).isEqualTo("Personne injoignable");
    }

    @Test
    void cancel_appliesDisplayNameMaskerToResponse() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(any(Audition.class))).thenReturn("Nom masqué");

        AuditionResponse response = service.cancel(audition.getId(), "Personne injoignable");

        assertThat(response.getIntervieweeDisplayName()).isEqualTo("Nom masqué");
    }

    @Test
    void cancel_throwsWhenAuditionNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.cancel(audition.getId(), "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markNoShow_setsStatusAndNote() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());

        service.markNoShow(audition.getId(), "Deux relances sans réponse");

        assertThat(audition.getStatus()).isEqualTo(AuditionStatus.NO_SHOW);
        assertThat(audition.getNoShowNote()).isEqualTo("Deux relances sans réponse");
    }

    @Test
    void markNoShow_appliesDisplayNameMaskerToResponse() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.SCHEDULED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));
        when(auditionRepository.save(any(Audition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(any(Audition.class))).thenReturn("Nom masqué");

        AuditionResponse response = service.markNoShow(audition.getId(), "Deux relances sans réponse");

        assertThat(response.getIntervieweeDisplayName()).isEqualTo("Nom masqué");
    }

    @Test
    void markNoShow_throwsWhenAuditionNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
        when(auditionRepository.findById(audition.getId()))
                .thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.markNoShow(audition.getId(), "motif"))
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
    void findByInvestigationId_appliesDisplayNameMaskerToResponse() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Audition audition = Audition.builder()
                .id(UUID.randomUUID())
                .intervieweeType(IntervieweeType.TARGETED_PARTY)
                .status(AuditionStatus.SCHEDULED)
                .investigation(investigation)
                .build();
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigation.getId()))
                .thenReturn(List.of(audition));
        when(mapper.toResponse(any(Audition.class)))
                .thenReturn(AuditionResponse.builder().build());
        when(displayNameMasker.mask(any(Audition.class))).thenReturn("Nom masqué");

        List<AuditionResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIntervieweeDisplayName()).isEqualTo("Nom masqué");
    }

    @Test
    void findByInvestigationId_throwsWhenInvestigationUnknown() {
        UUID id = UUID.randomUUID();
        when(investigationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByInvestigationId(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
