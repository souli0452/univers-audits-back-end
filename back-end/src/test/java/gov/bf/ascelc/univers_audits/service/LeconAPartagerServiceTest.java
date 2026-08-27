package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.LeconAPartagerRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeconAPartagerServiceTest {

    @Mock private LeconAPartagerRepository leconAPartagerRepository;
    @Mock private FicheRetexRepository ficheRetexRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private LeconAPartagerService service;

    private Investigation buildInvestigation(UUID id) {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        return Investigation.builder().id(id).dossier(dossier).build();
    }

    private PublierLeconRequest buildRequest() {
        return PublierLeconRequest.builder()
                .titre("Schéma de surfacturation détecté")
                .resume("Résumé de la leçon à partager")
                .build();
    }

    @Test
    void publier_creeUneLeconDepuisUneFicheExistante() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));
        when(leconAPartagerRepository.existsByFicheRetexId(fiche.getId())).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(leconAPartagerRepository.save(any(LeconAPartager.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeconAPartagerResponse result = service.publier(investigationId, buildRequest());

        assertThat(result.getTitre()).isEqualTo("Schéma de surfacturation détecté");
    }

    @Test
    void publier_refuseSiAucuneFicheRetex() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publier(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publier_refuseSiLeconDejaPubliee() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));
        when(leconAPartagerRepository.existsByFicheRetexId(fiche.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.publier(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publier_verifieLeControleDaccesViaDossierAccessGuard() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(ficheRetexRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(fiche));
        when(leconAPartagerRepository.existsByFicheRetexId(fiche.getId())).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(leconAPartagerRepository.save(any(LeconAPartager.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.publier(investigationId, buildRequest());

        verify(accessGuard).checkReadAccess(investigation.getDossier());
    }

    @Test
    void lister_neFiltrePasParHabilitationDossier() {
        Pageable pageable = PageRequest.of(0, 20);
        Investigation investigation = buildInvestigation(UUID.randomUUID());
        FicheRetex fiche = FicheRetex.builder().id(UUID.randomUUID()).investigation(investigation).build();
        LeconAPartager lecon = LeconAPartager.builder()
                .id(UUID.randomUUID())
                .ficheRetex(fiche)
                .titre("Titre")
                .resume("Résumé")
                .publieePar(Agent.builder().id(UUID.randomUUID()).build())
                .build();

        when(leconAPartagerRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(lecon)));

        Page<LeconAPartagerResponse> result = service.lister(pageable);

        assertThat(result.getContent()).hasSize(1);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void publier_refuseSiDossierConfidentielEtAgentNonHabilite() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = buildInvestigation(investigationId);
        investigation.getDossier().setIsConfidential(true);

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.publier(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }
}
