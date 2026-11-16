package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.RoleDefinition;
import gov.bf.ascelc.univers_audits.repository.RoleDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Au démarrage, crée dans Keycloak les rôles actifs de la base qui n'y existent pas encore. Une migration ne peut
 * pas créer un rôle Keycloak : sans cette synchronisation, un rôle ajouté par migration (comme « Chef du DEI »)
 * existerait en base mais ne pourrait être attribué à personne.
 *
 * <p>Ne crée que ce qui manque : elle ne supprime et ne modifie jamais rien dans Keycloak. Elle ne bloque jamais le
 * démarrage : une panne est seulement journalisée. Activée par {@code app.keycloak.sync-roles-on-startup=true}
 * (production) ; désactivée par défaut pour que les tests et le poste de développement ne touchent pas à Keycloak.
 */
@Slf4j
@Component
public class KeycloakRoleSynchronizer {

    private final RoleDefinitionRepository roles;
    private final KeycloakAdminService keycloak;
    private final boolean actif;

    public KeycloakRoleSynchronizer(RoleDefinitionRepository roles,
                                    KeycloakAdminService keycloak,
                                    @Value("${app.keycloak.sync-roles-on-startup:false}") boolean actif) {
        this.roles = roles;
        this.keycloak = keycloak;
        this.actif = actif;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchroniser() {
        if (!actif) {
            return;
        }
        try {
            Set<String> existants = new HashSet<>(keycloak.getAvailableRoles());
            List<RoleDefinition> aCreer = roles.findAll().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getActive()))
                    .filter(r -> !existants.contains(r.getRoleKey()))
                    .toList();
            for (RoleDefinition role : aCreer) {
                creer(role);
            }
            log.info("[KeycloakRoleSynchronizer] {} rôle(s) actif(s) en base, {} créé(s) dans Keycloak",
                    roles.count(), aCreer.size());
        } catch (RuntimeException e) {
            log.error("[KeycloakRoleSynchronizer] Synchronisation impossible, le démarrage continue : {}", e.getMessage());
        }
    }

    private void creer(RoleDefinition role) {
        try {
            keycloak.createRealmRole(role.getRoleKey(), role.getDescription());
        } catch (RuntimeException e) {
            log.error("[KeycloakRoleSynchronizer] Rôle '{}' non créé dans Keycloak : {}", role.getRoleKey(), e.getMessage());
        }
    }
}
