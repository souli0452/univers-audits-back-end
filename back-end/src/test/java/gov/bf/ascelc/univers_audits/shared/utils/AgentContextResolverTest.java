package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentContextResolverTest {

    @Mock private AgentRepository agentRepository;
    @Mock private SecurityUtils   securityUtils;

    @InjectMocks
    private AgentContextResolver resolver;

    @Test
    void getCurrentAgent_leveSiPersonneNEstAuthentifie() {
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        assertThatThrownBy(resolver::getCurrentAgent).isInstanceOf(BusinessException.class);
    }

    @Test
    void getCurrentAgentOrNull_retourneNullSiPersonneNEstAuthentifie() {
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        assertThat(resolver.getCurrentAgentOrNull()).isNull();
    }

    @Test
    void getCurrentAgentOrNull_retourneNullSiAgentIntrouvable() {
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.empty());

        assertThat(resolver.getCurrentAgentOrNull()).isNull();
    }

    @Test
    void getCurrentAgentOrNull_retourneLAgentSiAuthentifie() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(agent));

        assertThat(resolver.getCurrentAgentOrNull()).isEqualTo(agent);
    }
}
