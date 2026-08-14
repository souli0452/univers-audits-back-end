package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.RequeteParquetRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequeteParquetServiceTest {

    @Mock
    private RequeteParquetRepository requeteParquetRepository;
    @Mock
    private InvestigationRepository investigationRepository;
    @Mock
    private DossierAccessGuard accessGuard;

    @InjectMocks
    private RequeteParquetService service;

    private Investigation investigation;
    private UUID investigationId;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .outcome(InvestigationOutcome.JUDICIAL_REFERRAL)
                .dossier(dossier)
                .build();
    }

    private RequeteParquetRequest buildRequest() {
        return RequeteParquetRequest.builder()
                .contenu("Exposé des faits et qualification pénale")
                .build();
    }

    @Test
    void enregistrer_creeUneNouvelleRequeteSiAucuneNExisteEncore() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(requeteParquetRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(requeteParquetRepository.save(any(RequeteParquet.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RequeteParquet result = service.enregistrer(investigationId, buildRequest());

        assertThat(result.getInvestigation()).isEqualTo(investigation);
        assertThat(result.getContenu()).isEqualTo("Exposé des faits et qualification pénale");
        verify(requeteParquetRepository).save(any(RequeteParquet.class));
    }

    @Test
    void enregistrer_metAJourLaRequeteExistanteAuDeuxiemeAppel() {
        RequeteParquet existante = RequeteParquet.builder()
                .investigation(investigation)
                .contenu("Ancien contenu")
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(requeteParquetRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(existante));
        when(requeteParquetRepository.save(any(RequeteParquet.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RequeteParquet result = service.enregistrer(investigationId, buildRequest());

        assertThat(result).isSameAs(existante);
        assertThat(result.getContenu()).isEqualTo("Exposé des faits et qualification pénale");
        verify(requeteParquetRepository).save(existante);
    }

    @Test
    void enregistrer_rejetteSiOutcomeNestPasJudicialReferral() {
        investigation.setOutcome(InvestigationOutcome.ARCHIVED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.enregistrer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(requeteParquetRepository, never()).save(any());
    }

    @Test
    void enregistrer_rejetteSiDecisionFinaleDejaRendue() {
        investigation.setCgeApprovedAt(Instant.now());
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.enregistrer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(requeteParquetRepository, never()).save(any());
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiAucuneRequete() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(requeteParquetRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrThrow_propageBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(investigation.getDossier());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(requeteParquetRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie() {
        investigation.getDossier().setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(requeteParquetRepository, never()).findByInvestigationId(any());
    }
}
