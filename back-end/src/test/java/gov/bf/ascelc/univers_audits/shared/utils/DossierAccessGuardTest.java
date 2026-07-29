package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DossierAccessGuardTest {

    @Mock private DossierRepository             dossierRepository;
    @Mock private AgentRepository               agentRepository;
    @Mock private SecurityUtils                 securityUtils;
    @Mock private DossierHabilitationRepository habilitationRepository;

    @InjectMocks
    private DossierAccessGuard guard;

    @Test
    void checkReadAccess_allowsPrivilegedRoleRegardlessOfHabilitation() {
        when(securityUtils.hasRole("CGE")).thenReturn(true);

        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();

        assertThatCode(() -> guard.checkReadAccess(dossier)).doesNotThrowAnyException();
    }

    @Test
    void checkReadAccess_allowsAgentWithActiveHabilitation() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);

        UUID agentId   = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();

        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(true);

        assertThatCode(() -> guard.checkReadAccess(dossier)).doesNotThrowAnyException();
    }

    @Test
    void checkReadAccess_rejectsAgentWithoutHabilitation() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);

        UUID agentId   = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent   agent   = Agent.builder().id(agentId).build();

        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-1"));
        when(agentRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(agent));
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.checkReadAccess(dossier))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkReadAccess_rejectsUnauthenticatedAgent() {
        when(securityUtils.hasRole("CGE")).thenReturn(false);
        when(securityUtils.hasRole("CGEA")).thenReturn(false);
        when(securityUtils.hasRole("ADMIN_DDIC")).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();

        assertThatThrownBy(() -> guard.checkReadAccess(dossier))
                .isInstanceOf(BusinessException.class);
    }
}
