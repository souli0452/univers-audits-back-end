package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    private static final List<String> SYSTEM_ROLES = List.of(
            "offline_access", "uma_authorization",
            "default-roles-asce-lc", "create-realm"
    );

    private Keycloak buildAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("asce-lc")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    /**
     * Crée un utilisateur dans Keycloak.
     * @return l'UUID Keycloak (sub) du nouvel utilisateur
     */
    public String createUser(String email, String firstName,
                             String lastName, String username) {
        Keycloak kc = buildAdminClient();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username.toLowerCase());
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        try (Response response = kc.realm(realm).users().create(user)) {
            int status = response.getStatus();

            if (status == 409) {
                throw new ConflictException(
                        "L'email ou le nom d'utilisateur est déjà utilisé");
            }
            if (status != 201) {
                throw new RuntimeException(
                        "Échec création Keycloak, status: " + status);
            }

            String location = response.getHeaderString("Location");
            String keycloakId = location.substring(location.lastIndexOf('/') + 1);
            log.info("User Keycloak créé: {} (id={})", username, keycloakId);
            return keycloakId;
        }
    }

    /**
     * Assigne des rôles realm à un utilisateur Keycloak.
     */
    public void assignRoles(String keycloakId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return;
        if (keycloakId == null || keycloakId.isBlank()) return;

        try {
            RealmResource realmResource = buildAdminClient().realm(realm);
            List<RoleRepresentation> roles = roleNames.stream()
                    .map(name -> {
                        try {
                            return realmResource.roles().get(name).toRepresentation();
                        } catch (Exception e) {
                            log.warn("Rôle Keycloak introuvable: {}", name);
                            return null;
                        }
                    })
                    .filter(r -> r != null)
                    .toList();

            if (!roles.isEmpty()) {
                realmResource.users().get(keycloakId)
                        .roles().realmLevel().add(roles);
                log.info("Rôles assignés à {}: {}", keycloakId, roleNames);
            }
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Impossible d'assigner les rôles — utilisateur Keycloak introuvable : {}",
                    keycloakId);
        } catch (Exception e) {
            log.error("Erreur assignation rôles pour {} : {}",
                    keycloakId, e.getMessage());
        }
    }

    /**
     * Retire des rôles realm d'un utilisateur Keycloak.
     */
    public void removeRoles(String keycloakId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return;
        if (keycloakId == null || keycloakId.isBlank()) return;

        try {
            RealmResource realmResource = buildAdminClient().realm(realm);
            List<RoleRepresentation> roles = roleNames.stream()
                    .map(name -> {
                        try {
                            return realmResource.roles().get(name).toRepresentation();
                        } catch (Exception e) {
                            log.warn("Rôle Keycloak introuvable: {}", name);
                            return null;
                        }
                    })
                    .filter(r -> r != null)
                    .toList();

            if (!roles.isEmpty()) {
                realmResource.users().get(keycloakId)
                        .roles().realmLevel().remove(roles);
                log.info("Rôles retirés de {}: {}", keycloakId, roleNames);
            }
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Impossible de retirer les rôles — utilisateur Keycloak introuvable : {}",
                    keycloakId);
        } catch (Exception e) {
            log.error("Erreur suppression rôles pour {} : {}",
                    keycloakId, e.getMessage());
        }
    }



    public void sendPasswordResetEmail(String keycloakId) {
        try {
            buildAdminClient().realm(realm)
                    .users().get(keycloakId)
                    .executeActionsEmail(List.of("UPDATE_PASSWORD"));
            log.info("Email reset envoyé à: {}", keycloakId);
        } catch (Exception e) {
            log.error("Échec envoi email reset pour {}: {} - {}",
                    keycloakId,
                    e.getClass().getSimpleName(),
                    e.getMessage(), e);
        }
    }

    /**
     * Retourne tous les rôles métier du realm (filtre les rôles système).
     */
    public List<String> getAvailableRoles() {
        return buildAdminClient().realm(realm)
                .roles().list()
                .stream()
                .map(RoleRepresentation::getName)
                .filter(name -> !SYSTEM_ROLES.contains(name))
                .sorted()
                .toList();
    }

    /**
     * Retourne les rôles realm actuels d'un utilisateur.
     */
    public List<String> getUserRoles(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return buildAdminClient().realm(realm)
                    .users().get(keycloakId)
                    .roles().realmLevel().listEffective()
                    .stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !SYSTEM_ROLES.contains(name))
                    .toList();
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Utilisateur Keycloak introuvable pour id: {}", keycloakId);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Erreur lecture rôles Keycloak pour {}: {}",
                    keycloakId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Active ou désactive un utilisateur dans Keycloak.
     */
    public void setUserEnabled(String keycloakId, boolean enabled) {
        Keycloak kc = buildAdminClient();
        UserResource userResource = kc.realm(realm).users().get(keycloakId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(enabled);
        userResource.update(user);
        log.info("User Keycloak {} {}",
                keycloakId, enabled ? "activé" : "désactivé");
    }

    /**
     * Modifie le profil d'un utilisateur Keycloak.
     */
    public void updateUserProfile(String keycloakId,
                                  String firstName,
                                  String lastName,
                                  String email) {
        Keycloak kc = buildAdminClient();
        UserResource userResource = kc.realm(realm).users().get(keycloakId);
        UserRepresentation user = userResource.toRepresentation();

        if (firstName != null) user.setFirstName(firstName);
        if (lastName  != null) user.setLastName(lastName);
        if (email     != null) {
            user.setEmail(email);
            user.setEmailVerified(true);
        }

        userResource.update(user);
        log.info("Profil mis à jour pour: {}", keycloakId);
    }

    /**
     * Change le mot de passe d'un utilisateur.
     */
    public void changePassword(String keycloakId, String newPassword) {
        Keycloak kc = buildAdminClient();

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);

        kc.realm(realm).users().get(keycloakId).resetPassword(credential);
        log.info("Mot de passe changé pour: {}", keycloakId);
    }
}