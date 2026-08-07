package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
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
class VisiteTerrainServiceImplTest {

    @Mock private VisiteTerrainRepository visiteTerrainRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierDetailsMapper    mapper;
    @Mock private AgentContextResolver    agentContextResolver;
    @Mock private DossierAccessGuard      accessGuard;

    @InjectMocks
    private VisiteTerrainServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
    }

    @Test
    void schedule_createsVisite() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        VisiteTerrainScheduleRequest request = VisiteTerrainScheduleRequest.builder()
                .location("Siège de l'entreprise X")
                .scheduledAt(Instant.now())
                .build();

        service.schedule(investigation.getId(), request);

        verify(visiteTerrainRepository).save(argThat(v ->
                v.getLocation().equals("Siège de l'entreprise X")
                        && v.getStatus() == VisiteStatus.SCHEDULED
                        && v.getPlannedBy() == agent));
    }

    @Test
    void conduct_movesToConductedWhenScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.SCHEDULED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        VisiteTerrainConductRequest request = VisiteTerrainConductRequest.builder()
                .summary("Site visité, documents comptables observés").build();

        service.conduct(visite.getId(), request);

        assertThat(visite.getStatus()).isEqualTo(VisiteStatus.CONDUCTED);
        assertThat(visite.getConductedAt()).isNotNull();
    }

    @Test
    void conduct_throwsWhenNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.CANCELLED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));

        VisiteTerrainConductRequest request = VisiteTerrainConductRequest.builder()
                .summary("Résumé").build();

        assertThatThrownBy(() -> service.conduct(visite.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancel_movesToCancelledWhenScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.SCHEDULED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        service.cancel(visite.getId(), "Mission reportée");

        assertThat(visite.getStatus()).isEqualTo(VisiteStatus.CANCELLED);
        assertThat(visite.getCancellationReason()).isEqualTo("Mission reportée");
    }

    @Test
    void cancel_throwsWhenNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.CONDUCTED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));

        assertThatThrownBy(() -> service.cancel(visite.getId(), "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markCarence_movesToCarenceWhenScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.SCHEDULED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(visiteTerrainRepository.save(any(VisiteTerrain.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(VisiteTerrain.class)))
                .thenReturn(VisiteTerrainResponse.builder().build());

        service.markCarence(visite.getId(), "Site inaccessible, portail fermé");

        assertThat(visite.getStatus()).isEqualTo(VisiteStatus.CARENCE);
        assertThat(visite.getCarenceReason()).isEqualTo("Site inaccessible, portail fermé");
    }

    @Test
    void markCarence_throwsWhenNotScheduled() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        VisiteTerrain visite = VisiteTerrain.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .status(VisiteStatus.CONDUCTED)
                .build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));

        assertThatThrownBy(() -> service.markCarence(visite.getId(), "motif"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByInvestigationId_returnsEmptyWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<VisiteTerrainResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).isEmpty();
        verify(visiteTerrainRepository, never()).findByInvestigationIdOrderByScheduledAtAsc(any());
    }

    @Test
    void findByInvestigationId_throwsWhenInvestigationUnknown() {
        UUID id = UUID.randomUUID();
        when(investigationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByInvestigationId(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
