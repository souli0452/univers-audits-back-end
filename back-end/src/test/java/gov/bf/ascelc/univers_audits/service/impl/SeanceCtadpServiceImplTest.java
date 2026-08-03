package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.mapper.SeanceCtadpMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeanceCtadpServiceImplTest {

    @Mock private SeanceCtadpRepository        seanceCtadpRepository;
    @Mock private SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    @Mock private DossierRepository            dossierRepository;
    @Mock private SeanceCtadpMapper             mapper;

    @InjectMocks
    private SeanceCtadpServiceImpl service;

    @Test
    void findAll_doesNotPopulateDossiersToAvoidLazyLoading() {
        Pageable pageable = PageRequest.of(0, 20);
        SeanceCTADP seance = SeanceCTADP.builder().id(UUID.randomUUID())
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Page<SeanceCTADP> page = new PageImpl<>(List.of(seance));

        when(seanceCtadpRepository.findAll(pageable)).thenReturn(page);
        when(mapper.mapToResponse(seance)).thenReturn(SeanceCtadpResponse.builder().build());

        Page<SeanceCtadpResponse> result = service.findAll(pageable);

        assertThat(result.getContent().get(0).getDossiers()).isEmpty();
        verify(mapper, never()).toResponse(any(SeanceCTADP.class));
    }

    @Test
    void addDossier_succeedsWhenDossierEligibleAndSeancePlanifiee() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(seanceCtadpDossierRepository.existsBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(false);
        when(seanceCtadpRepository.save(any(SeanceCTADP.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SeanceCTADP.class)))
                .thenReturn(SeanceCtadpResponse.builder().build());

        service.addDossier(seanceId, request);

        verify(seanceCtadpRepository).save(argThat(s -> s.getDossiers().size() == 1
                && s.getDossiers().get(0).getDossier() == dossier));
    }

    @Test
    void addDossier_rejectsWhenDossierNotInCorrectStatus() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.RECU).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.addDossier(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
    }

    @Test
    void addDossier_rejectsWhenAlreadyOnAgenda() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(seanceCtadpDossierRepository.existsBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addDossier(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
    }

    @Test
    void addDossier_rejectsWhenSeanceNotPlanifiee() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.TENUE).build();
        AddDossierToSeanceRequest request = AddDossierToSeanceRequest.builder()
                .dossierId(dossierId).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));

        assertThatThrownBy(() -> service.addDossier(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
        verifyNoInteractions(dossierRepository);
    }

    @Test
    void recordRecommandation_updatesEntryAndReturnsFullSeance() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId).build();
        SeanceCtadpDossier entry = SeanceCtadpDossier.builder().id(UUID.randomUUID()).build();
        RecommandationCtadpRequest request = RecommandationCtadpRequest.builder()
                .recommandation(RecommandationCtadp.VALIDATION_INVESTIGATION)
                .commentaire("Preuves suffisantes")
                .build();

        when(seanceCtadpRepository.findById(seanceId))
                .thenReturn(Optional.of(seance), Optional.of(seance));
        when(seanceCtadpDossierRepository.findBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(Optional.of(entry));
        when(mapper.toResponse(seance)).thenReturn(SeanceCtadpResponse.builder().build());

        service.recordRecommandation(seanceId, dossierId, request);

        verify(seanceCtadpDossierRepository).save(argThat(e ->
                e.getRecommandation() == RecommandationCtadp.VALIDATION_INVESTIGATION
                        && "Preuves suffisantes".equals(e.getCommentaire())));
    }

    @Test
    void recordRecommandation_rejectsWhenDossierNotOnAgenda() {
        UUID seanceId = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId).build();
        RecommandationCtadpRequest request = RecommandationCtadpRequest.builder()
                .recommandation(RecommandationCtadp.CLASSEMENT).build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(seanceCtadpDossierRepository.findBySeanceCtadpIdAndDossierId(seanceId, dossierId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordRecommandation(seanceId, dossierId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException.class);

        verify(seanceCtadpDossierRepository, never()).save(any());
    }

    @Test
    void tenir_succeedsWhenPlanifiee() {
        UUID seanceId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.PLANIFIEE).build();
        TenirSeanceRequest request = TenirSeanceRequest.builder()
                .procesVerbal("Compte-rendu de la séance").build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));
        when(seanceCtadpRepository.save(any(SeanceCTADP.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(SeanceCTADP.class)))
                .thenReturn(SeanceCtadpResponse.builder().build());

        service.tenir(seanceId, request);

        verify(seanceCtadpRepository).save(argThat(s ->
                s.getStatut() == StatutSeanceCtadp.TENUE
                        && "Compte-rendu de la séance".equals(s.getProcesVerbal())));
    }

    @Test
    void tenir_rejectsWhenAlreadyTenue() {
        UUID seanceId = UUID.randomUUID();
        SeanceCTADP seance = SeanceCTADP.builder().id(seanceId)
                .statut(StatutSeanceCtadp.TENUE).build();
        TenirSeanceRequest request = TenirSeanceRequest.builder().build();

        when(seanceCtadpRepository.findById(seanceId)).thenReturn(Optional.of(seance));

        assertThatThrownBy(() -> service.tenir(seanceId, request))
                .isInstanceOf(gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException.class);

        verify(seanceCtadpRepository, never()).save(any());
    }
}
