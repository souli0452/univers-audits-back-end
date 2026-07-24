package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Résolution de l'Agent correspondant à l'utilisateur Keycloak courant —
 * logique auparavant dupliquée à l'identique dans DossierServiceImpl,
 * InvestigationServiceImpl et ObservationServiceImpl.
 */
@Component
@RequiredArgsConstructor
public class AgentContextResolver {

    private final AgentRepository agentRepository;
    private final SecurityUtils   securityUtils;

    public Agent getCurrentAgent() {
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
    }
}
