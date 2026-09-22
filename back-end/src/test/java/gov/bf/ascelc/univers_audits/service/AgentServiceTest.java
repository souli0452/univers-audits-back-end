package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private KeycloakAdminService keycloakAdminService;

    @InjectMocks
    private AgentService service;

    @Test
    void findActiveByKeycloakRole_neRetourneQueLesAgentsActifsRetrouvesEnBase() {
        Agent actif = Agent.builder().keycloakId("kc-1").actif(true).build();

        when(keycloakAdminService.getUserIdsByRole("CONSEILLER_JURIDIQUE"))
                .thenReturn(List.of("kc-1", "kc-2", "kc-3"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(actif));
        // kc-2 : Keycloak connaît le user mais aucun Agent correspondant en base
        when(agentRepository.findByKeycloakId("kc-2")).thenReturn(Optional.empty());
        // kc-3 : Agent trouvé mais inactif
        when(agentRepository.findByKeycloakId("kc-3"))
                .thenReturn(Optional.of(Agent.builder().keycloakId("kc-3").actif(false).build()));

        List<Agent> result = service.findActiveByKeycloakRole("CONSEILLER_JURIDIQUE");

        assertThat(result).containsExactly(actif);
    }

    @Test
    void findActiveByKeycloakRole_retourneListeVideSiAucunMembre() {
        when(keycloakAdminService.getUserIdsByRole("ROLE_INCONNU")).thenReturn(List.of());

        List<Agent> result = service.findActiveByKeycloakRole("ROLE_INCONNU");

        assertThat(result).isEmpty();
    }
}
