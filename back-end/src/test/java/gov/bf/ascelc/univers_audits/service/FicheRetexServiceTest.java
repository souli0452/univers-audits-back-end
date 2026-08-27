package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FicheRetexServiceTest {

    @Mock private FicheRetexRepository ficheRetexRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private TypeInfractionRepository typeInfractionRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private FicheRetexService service;

    private Investigation buildInvestigation(UUID id, Instant cgeApprovedAt) {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        return Investigation.builder()
                .id(id)
                .dossier(dossier)
                .cgeApprovedAt(cgeApprovedAt)
                .build();
    }

    private FicheRetexRequest buildRequest() {
        return FicheRetexRequest.builder()
                .syntheseResultats("Synthèse des résultats")
                .enseignementsAxesAmelioration("Enseignements tirés")
                .build();
    }

    @Test
    void creer_creeUneFicheRetexApresDecisionCge() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(ficheRetexRepository.save(any(FicheRetex.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FicheRetexResponse result = service.creer(investigationId, buildRequest());

        assertThat(result.getSyntheseResultats()).isEqualTo("Synthèse des résultats");
        assertThat(result.getInvestigationId()).isEqualTo(investigationId);
    }

    @Test
    void creer_refuseSiCgeApprovedAtNull() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, null);

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.creer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void creer_refuseSiFicheDejaExistante() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        FicheRetex existing = FicheRetex.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.creer(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void creer_resoutLeTypeInfractionSiFourni() {
        UUID investigationId = UUID.randomUUID();
        UUID typeInfractionId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        TypeInfraction typeInfraction = TypeInfraction.builder()
                .id(typeInfractionId)
                .libelle("Corruption")
                .build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        FicheRetexRequest request = FicheRetexRequest.builder()
                .typeInfractionId(typeInfractionId)
                .syntheseResultats("Synthèse")
                .enseignementsAxesAmelioration("Enseignements")
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(typeInfractionRepository.findById(typeInfractionId)).thenReturn(Optional.of(typeInfraction));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(ficheRetexRepository.save(any(FicheRetex.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FicheRetexResponse result = service.creer(investigationId, request);

        assertThat(result.getTypeInfractionLibelle()).isEqualTo("Corruption");
    }

    @Test
    void creer_refuseSiTypeInfractionIntrouvable() {
        UUID investigationId = UUID.randomUUID();
        UUID typeInfractionId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());

        FicheRetexRequest request = FicheRetexRequest.builder()
                .typeInfractionId(typeInfractionId)
                .syntheseResultats("Synthèse")
                .enseignementsAxesAmelioration("Enseignements")
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(typeInfractionRepository.findById(typeInfractionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creer(investigationId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creer_verifieLeControleDaccesViaDossierAccessGuard() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(ficheRetexRepository.save(any(FicheRetex.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.creer(investigationId, buildRequest());

        verify(accessGuard).checkReadAccess(investigation.getDossier());
    }

    @Test
    void obtenir_retourneLaFicheExistante() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());
        FicheRetex fiche = FicheRetex.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .syntheseResultats("Synthèse")
                .enseignementsAxesAmelioration("Enseignements")
                .redigePar(Agent.builder().id(UUID.randomUUID()).build())
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));

        FicheRetexResponse result = service.obtenir(investigationId);

        assertThat(result.getSyntheseResultats()).isEqualTo("Synthèse");
    }

    @Test
    void obtenir_leveResourceNotFoundSiAucuneFiche() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId, Instant.now());

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
