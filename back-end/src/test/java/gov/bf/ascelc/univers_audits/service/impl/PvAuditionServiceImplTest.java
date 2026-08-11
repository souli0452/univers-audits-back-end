package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.CorrectionPvAudition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.CorrectionPvAuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
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
class PvAuditionServiceImplTest {

    @Mock private PVAuditionRepository pvAuditionRepository;
    @Mock private CorrectionPvAuditionRepository correctionPvAuditionRepository;
    @Mock private AuditionRepository auditionRepository;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private PvAuditionServiceImpl service;

    private Audition buildAudition(Dossier dossier) {
        return buildAudition(dossier, AuditionStatus.CONDUCTED);
    }

    private Audition buildAudition(Dossier dossier, AuditionStatus status) {
        return Audition.builder()
                .id(UUID.randomUUID())
                .status(status)
                .investigation(Investigation.builder().dossier(dossier).build())
                .build();
    }

    @Test
    void create_savesPvWithContentAndDraftedBy() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("Procès-verbal...").build());

        verify(pvAuditionRepository).save(argThat(pv ->
                pv.getContent().equals("Procès-verbal...")
                        && pv.getDraftedBy() == agent
                        && pv.getAudition() == audition));
    }

    @Test
    void create_throwsWhenPvAlreadyExistsForAudition() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(pvAuditionRepository.findByAuditionId(audition.getId()))
                .thenReturn(Optional.of(PVAudition.builder().build()));

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_throwsWhenAuditionNotConducted() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier, AuditionStatus.NO_SHOW);
        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);

        verify(pvAuditionRepository, never()).save(any());
    }

    @Test
    void create_throwsWhenDossierConfidentialAndAgentCannotSeeConfidential() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Audition audition = buildAudition(dossier);

        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_throwsWhenAgentLacksReadAccess() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder().id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED).investigation(investigation).build();

        when(auditionRepository.findById(audition.getId())).thenReturn(Optional.of(audition));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.create(audition.getId(),
                PvAuditionCreateRequest.builder().content("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markReadBack_setsReadBackAt() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.markReadBack(pv.getId());

        assertThat(pv.getReadBackAt()).isNotNull();
    }

    @Test
    void markReadBack_throwsWhenDossierConfidentialAndAgentCannotSeeConfidential() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.markReadBack(pv.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markReadBack_throwsWhenAlreadySet() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .readBackAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        assertThatThrownBy(() -> service.markReadBack(pv.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markReadBack_throwsWhenAlreadyFinalized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .finalizedAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        assertThatThrownBy(() -> service.markReadBack(pv.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenDossierConfidentialAndAgentCannotSeeConfidential() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenSignedAndRefusedBothTrue() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(true)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenAlreadyFinalized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder()
                .id(UUID.randomUUID())
                .audition(audition)
                .finalizedAt(java.time.Instant.now())
                .build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void finalizeSignatures_throwsWhenNotReadBack() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        assertThatThrownBy(() -> service.finalizeSignatures(pv.getId(), request))
                .isInstanceOf(BusinessException.class);

        assertThat(pv.isFinalized()).isFalse();
    }

    @Test
    void finalizeSignatures_succeedsWhenReadBackAtIsSet() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .readBackAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        PvAuditionFinalizeRequest request = PvAuditionFinalizeRequest.builder()
                .intervieweeSigned(true)
                .intervieweeSignatureRefused(false)
                .build();

        service.finalizeSignatures(pv.getId(), request);

        assertThat(pv.isFinalized()).isTrue();
    }

    @Test
    void correct_throwsWhenDossierConfidentialAndAgentCannotSeeConfidential() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition)
                .finalizedAt(Instant.now()).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.correct(pv.getId(),
                PvAuditionCorrectionRequest.builder()
                        .content("Nouveau contenu")
                        .motifCorrection("Erreur de transcription")
                        .build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void correct_throwsWhenNotFinalized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));

        assertThatThrownBy(() -> service.correct(pv.getId(),
                PvAuditionCorrectionRequest.builder()
                        .content("Nouveau contenu")
                        .motifCorrection("Erreur de transcription")
                        .build()))
                .isInstanceOf(BusinessException.class);

        verify(correctionPvAuditionRepository, never()).save(any());
    }

    @Test
    void correct_archivesOldContentAndAppliesNewOne() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        PVAudition pv = PVAudition.builder()
                .id(UUID.randomUUID())
                .audition(audition)
                .content("Contenu original")
                .pvVersion(1)
                .finalizedAt(Instant.now())
                .build();
        when(pvAuditionRepository.findByAuditionId(pv.getId())).thenReturn(Optional.of(pv));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvAuditionRepository.save(any(PVAudition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVAudition.class)))
                .thenReturn(PvAuditionResponse.builder().build());

        service.correct(pv.getId(), PvAuditionCorrectionRequest.builder()
                .content("Contenu corrigé")
                .motifCorrection("Erreur de transcription")
                .build());

        verify(correctionPvAuditionRepository).save(argThat(c ->
                c.getContent().equals("Contenu original")
                        && c.getVersionNumber().equals(1)
                        && c.getCorrectedBy() == agent
                        && c.getMotifCorrection().equals("Erreur de transcription")));
        assertThat(pv.getContent()).isEqualTo("Contenu corrigé");
        assertThat(pv.getPvVersion()).isEqualTo(2);
    }

    @Test
    void findByAuditionId_throwsWhenNoPvExists() {
        UUID auditionId = UUID.randomUUID();
        when(pvAuditionRepository.findByAuditionId(auditionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByAuditionId(auditionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByAuditionId_throwsWhenDossierConfidentialAndAgentCannotSeeConfidential() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = Investigation.builder().dossier(dossier).build();
        Audition audition = Audition.builder().id(UUID.randomUUID())
                .status(AuditionStatus.CONDUCTED).investigation(investigation).build();
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();

        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.findByAuditionId(audition.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByAuditionId_returnsCorrectionsInVersionOrder() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Audition audition = buildAudition(dossier);
        PVAudition pv = PVAudition.builder().id(UUID.randomUUID()).audition(audition).build();

        when(pvAuditionRepository.findByAuditionId(audition.getId())).thenReturn(Optional.of(pv));
        when(mapper.toResponse(pv)).thenReturn(PvAuditionResponse.builder().build());
        when(correctionPvAuditionRepository.findByPvAuditionIdOrderByVersionNumberAsc(pv.getId()))
                .thenReturn(List.of(
                        CorrectionPvAudition.builder()
                                .id(UUID.randomUUID())
                                .versionNumber(1)
                                .content("Contenu original")
                                .correctedAt(Instant.now())
                                .correctedBy(Agent.builder().id(UUID.randomUUID()).build())
                                .motifCorrection("Erreur de transcription")
                                .build()));

        PvAuditionResponse response = service.findByAuditionId(audition.getId());

        assertThat(response.getCorrections()).hasSize(1);
        assertThat(response.getCorrections().get(0).getVersionNumber()).isEqualTo(1);
    }
}
