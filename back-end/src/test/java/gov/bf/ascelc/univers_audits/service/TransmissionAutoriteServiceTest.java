package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TransmissionAutoriteRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransmissionAutoriteServiceTest {

    @Mock private TransmissionAutoriteRepository transmissionAutoriteRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private ParametreDelaiService parametreDelaiService;

    @InjectMocks
    private TransmissionAutoriteService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .build();
    }

    @Test
    void creer_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Procureur du Faso").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiTransmissionDejaExistante() {
        TransmissionAutorite existante = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur")
                .transmittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(existante));

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Autre autorité").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Procureur du Faso").build();

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void creer_succeedsEtRenseigneTransmittedAtEtTransmittedBy() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(transmissionAutoriteRepository.save(any(TransmissionAutorite.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransmissionAutoriteRequest request = TransmissionAutoriteRequest.builder()
                .autoriteDestinataire("Procureur du Faso").build();

        TransmissionAutoriteResponse result = service.creer(investigationId, request);

        assertThat(result.getAutoriteDestinataire()).isEqualTo("Procureur du Faso");
        assertThat(result.getTransmittedAt()).isNotNull();
        assertThat(result.getTransmittedByNom()).isEqualTo("Jean Ouedraogo");
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiAucuneTransmission() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transmissionAutoriteRepository, never()).findByInvestigationId(any());
    }

    @Test
    void ajouterRelance_rejetteSiAucuneTransmission() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        RelanceSuitesRequest request = RelanceSuitesRequest.builder().contenu("Relance").build();

        assertThatThrownBy(() -> service.ajouterRelance(investigationId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ajouterRelance_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        RelanceSuitesRequest request = RelanceSuitesRequest.builder().contenu("Relance").build();

        assertThatThrownBy(() -> service.ajouterRelance(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(transmissionAutoriteRepository, never()).save(any());
    }

    @Test
    void ajouterRelance_ajouteALaListeExistante() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(Instant.now())
                .transmittedBy(agent)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(transmissionAutoriteRepository.save(any(TransmissionAutorite.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RelanceSuitesRequest request = RelanceSuitesRequest.builder().contenu("Relance formelle").build();

        TransmissionAutoriteResponse result = service.ajouterRelance(investigationId, request);

        assertThat(result.getRelances()).hasSize(1);
        assertThat(result.getRelances().get(0).getContenu()).isEqualTo("Relance formelle");
        assertThat(result.getRelances().get(0).getAgentNom()).isEqualTo("Awa Sawadogo");
    }

    @Test
    void relanceOverdue_vraiSiEcheanceDepasseeEtAucuneRelanceEnvoyee() {
        Instant transmittedAt = Instant.now().minusSeconds(40L * 24 * 3600);
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(transmittedAt)
                .transmittedBy(agent)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(parametreDelaiService.resolveDelaiJours("RELANCE_SUITES_TRANSMISSION")).thenReturn(30);

        TransmissionAutoriteResponse result = service.getOrThrow(investigationId);

        assertThat(result.getRelanceOverdue()).isTrue();
    }

    @Test
    void relanceOverdue_fauxSiUneRelanceDejaEnvoyee() {
        Instant transmittedAt = Instant.now().minusSeconds(40L * 24 * 3600);
        Agent transmetteur = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(transmittedAt)
                .transmittedBy(transmetteur)
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        transmission.getRelances().add(RelanceSuites.builder()
                .transmissionAutorite(transmission)
                .relanceAt(Instant.now())
                .agent(agent)
                .contenu("Relance envoyée")
                .build());
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(parametreDelaiService.resolveDelaiJours("RELANCE_SUITES_TRANSMISSION")).thenReturn(30);

        TransmissionAutoriteResponse result = service.getOrThrow(investigationId);

        assertThat(result.getRelanceOverdue()).isFalse();
    }

    @Test
    void relanceOverdue_degradeVersNullSiParametreIndisponible() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        TransmissionAutorite transmission = TransmissionAutorite.builder()
                .investigation(investigation)
                .autoriteDestinataire("Procureur du Faso")
                .transmittedAt(Instant.now())
                .transmittedBy(agent)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(transmissionAutoriteRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(transmission));
        when(parametreDelaiService.resolveDelaiJours("RELANCE_SUITES_TRANSMISSION"))
                .thenThrow(new ResourceNotFoundException("Paramètre introuvable"));

        TransmissionAutoriteResponse result = service.getOrThrow(investigationId);

        assertThat(result.getRelanceDueAt()).isNull();
        assertThat(result.getRelanceOverdue()).isFalse();
    }
}
