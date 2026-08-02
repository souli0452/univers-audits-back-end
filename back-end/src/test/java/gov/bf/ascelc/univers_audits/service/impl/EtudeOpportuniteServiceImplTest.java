package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import gov.bf.ascelc.univers_audits.repository.EtudeOpportuniteRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtudeOpportuniteServiceImplTest {

    @Mock private EtudeOpportuniteRepository etudeOpportuniteRepository;
    @Mock private TypeInfractionRepository   typeInfractionRepository;
    @Mock private DossierDetailsMapper       detailsMapper;
    @Mock private DossierAccessGuard         accessGuard;

    @InjectMocks
    private EtudeOpportuniteServiceImpl service;

    @Test
    void upsert_createsWhenAbsent() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder()
                .preoccupationReelle(true)
                .build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(etudeOpportuniteRepository.findByDossierId(dossierId))
                .thenReturn(Optional.empty());
        when(etudeOpportuniteRepository.save(any(EtudeOpportunite.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(EtudeOpportunite.class)))
                .thenReturn(EtudeOpportuniteResponse.builder().build());

        service.upsert(dossierId, request);

        verify(accessGuard).checkReadAccess(dossier);
        verify(etudeOpportuniteRepository).save(argThat(e -> e.getDossier() == dossier));
    }

    @Test
    void upsert_rejectsWhenDossierNotInOpportunityStudyStatus() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder().build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);

        assertThatThrownBy(() -> service.upsert(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(etudeOpportuniteRepository, never()).save(any());
    }

    @Test
    void upsert_propagatesGuardRejectionWithoutSaving() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder().build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.upsert(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(etudeOpportuniteRepository, never()).save(any());
    }
}
