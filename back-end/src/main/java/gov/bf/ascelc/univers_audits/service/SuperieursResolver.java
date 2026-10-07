package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agents actifs porteurs du rôle CGEA ou CGE, destinataires des alertes et escalades automatiques.
 *
 * <p>Même règle que l'escalade automatique de {@code NotificationServiceImpl} ; ce composant évite de la
 * recopier dans chaque nouvelle alerte.
 */
@Component
@RequiredArgsConstructor
public class SuperieursResolver {

    private final KeycloakAdminService keycloakAdminService;
    private final AgentRepository agentRepository;

    public List<Agent> resoudre() {
        Set<String> keycloakIds = new LinkedHashSet<>();
        keycloakIds.addAll(keycloakAdminService.getUserIdsByRole("CGEA"));
        keycloakIds.addAll(keycloakAdminService.getUserIdsByRole("CGE"));

        List<Agent> superieurs = new ArrayList<>();
        for (String keycloakId : keycloakIds) {
            agentRepository.findByKeycloakId(keycloakId)
                    .filter(Agent::getActif)
                    .ifPresent(superieurs::add);
        }
        return superieurs;
    }
}
