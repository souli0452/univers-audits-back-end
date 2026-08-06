package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.PVConstatRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PvConstatServiceImplTest {

    @Mock private PVConstatRepository     pvConstatRepository;
    @Mock private VisiteTerrainRepository visiteTerrainRepository;
    @Mock private DossierDetailsMapper    mapper;
    @Mock private AgentContextResolver    agentContextResolver;
    @Mock private DossierAccessGuard      accessGuard;

    @InjectMocks
    private PvConstatServiceImpl service;

    @Test
    void create_savesPvWhenNoneExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
        VisiteTerrain visite = VisiteTerrain.builder().id(UUID.randomUUID()).investigation(investigation).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(pvConstatRepository.findByVisiteTerrainId(visite.getId()))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(pvConstatRepository.save(any(PVConstat.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(PVConstat.class)))
                .thenReturn(PvConstatResponse.builder().build());

        PvConstatCreateRequest request = PvConstatCreateRequest.builder()
                .content("Locaux vides, aucune activité constatée").build();

        service.create(visite.getId(), request);

        verify(pvConstatRepository).save(argThat(pv ->
                pv.getContent().equals("Locaux vides, aucune activité constatée")
                        && pv.getDraftedBy() == agent
                        && pv.getVisiteTerrain() == visite));
    }

    @Test
    void create_throwsWhenPvAlreadyExists() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
        VisiteTerrain visite = VisiteTerrain.builder().id(UUID.randomUUID()).investigation(investigation).build();

        when(visiteTerrainRepository.findById(visite.getId()))
                .thenReturn(Optional.of(visite));
        when(pvConstatRepository.findByVisiteTerrainId(visite.getId()))
                .thenReturn(Optional.of(PVConstat.builder().id(UUID.randomUUID()).build()));

        PvConstatCreateRequest request = PvConstatCreateRequest.builder()
                .content("Contenu").build();

        assertThatThrownBy(() -> service.create(visite.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByVisiteId_throwsWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
        VisiteTerrain visite = VisiteTerrain.builder().id(UUID.randomUUID()).investigation(investigation).build();
        PVConstat pv = PVConstat.builder().id(UUID.randomUUID()).visiteTerrain(visite).build();

        when(pvConstatRepository.findByVisiteTerrainId(visite.getId()))
                .thenReturn(Optional.of(pv));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.findByVisiteId(visite.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByVisiteId_throwsWhenNotFound() {
        UUID visiteId = UUID.randomUUID();
        when(pvConstatRepository.findByVisiteTerrainId(visiteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByVisiteId(visiteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
