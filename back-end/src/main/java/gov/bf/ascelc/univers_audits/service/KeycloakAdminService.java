package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
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

import java.util.*;
import java.util.stream.Collectors;

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

    private Keycloak keycloakClient;

    private static final List<String> SYSTEM_ROLES = List.of(
            "offline_access", "uma_authorization",
            "default-roles-asce-lc", "create-realm"
    );


    @PostConstruct
    private void init() {

        this.keycloakClient = buildClient();
        log.info("KeycloakAdminService initialisé — serverUrl={}, realm={}", serverUrl, realm);
    }

    private Keycloak buildClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }


    private RealmResource realmResource() {
        return keycloakClient.realm(realm);
    }

    public String createUser(String email, String firstName,
                             String lastName, String username) {

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username.toLowerCase());
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        try (Response response = realmResource().users().create(user)) {
            int status = response.getStatus();

            if (status == 409) throw new ConflictException(
                    "L'email ou le nom d'utilisateur est déjà utilisé");
            if (status != 201) throw new RuntimeException(
                    "Échec création Keycloak, status: " + status);

            String location  = response.getHeaderString("Location");
            String keycloakId = location.substring(location.lastIndexOf('/') + 1);
            log.info("User Keycloak créé: {} (id={})", username, keycloakId);
            return keycloakId;
        }
    }

    public void assignRoles(String keycloakId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return;
        if (keycloakId == null || keycloakId.isBlank()) return;

        try {
            List<RoleRepresentation> roles = resolveRolesBulk(roleNames);
            if (!roles.isEmpty()) {
                realmResource().users().get(keycloakId)
                        .roles().realmLevel().add(roles);
                log.info("Rôles assignés à {}: {}", keycloakId, roleNames);
            }
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new RuntimeException(
                    "Impossible d'assigner les rôles — utilisateur Keycloak introuvable : "
                            + keycloakId, e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Erreur assignation rôles pour " + keycloakId + " : " + e.getMessage(), e);
        }
    }

    public void removeRoles(String keycloakId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return;
        if (keycloakId == null || keycloakId.isBlank()) return;

        try {
            List<RoleRepresentation> roles = resolveRolesBulk(roleNames);
            if (!roles.isEmpty()) {
                realmResource().users().get(keycloakId)
                        .roles().realmLevel().remove(roles);
                log.info("Rôles retirés de {}: {}", keycloakId, roleNames);
            }
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new RuntimeException(
                    "Impossible de retirer les rôles — utilisateur Keycloak introuvable : "
                            + keycloakId, e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Erreur suppression rôles pour " + keycloakId + " : " + e.getMessage(), e);
        }
    }

    public void createRealmRole(String roleKey, String description) {
        try {
            boolean exists = realmResource().roles().list().stream()
                    .anyMatch(r -> r.getName().equals(roleKey));
            if (exists) throw new ConflictException(
                    "Le rôle '" + roleKey + "' existe déjà dans Keycloak.");

            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleKey);
            role.setDescription(description != null ? description : "");
            role.setClientRole(false);

            realmResource().roles().create(role);
            log.info("Rôle Keycloak créé : {}", roleKey);

        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur création rôle '{}': {}", roleKey, e.getMessage(), e);
            throw new RuntimeException(
                    "Impossible de créer le rôle dans Keycloak : " + e.getMessage());
        }
    }

    /**
     * Compensation pour un échec après création — supprime un user Keycloak
     * qui n'a pas d'Agent correspondant en base (dual-write non transactionnel
     * entre Keycloak et la DB).
     */
    public void deleteUser(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) return;
        try {
            realmResource().users().get(keycloakId).remove();
            log.warn("User Keycloak supprimé (compensation) : {}", keycloakId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("User Keycloak déjà absent lors de la compensation : {}", keycloakId);
        } catch (Exception e) {
            log.error("Échec de la suppression de compensation du user Keycloak {} : {}",
                    keycloakId, e.getMessage(), e);
        }
    }

    public void deleteRealmRole(String roleKey) {
        try {
            realmResource().roles().get(roleKey).remove();
            log.warn("Rôle Keycloak supprimé : {}", roleKey);
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Rôle '{}' introuvable lors de la suppression — ignoré", roleKey);
        } catch (Exception e) {
            log.error("Erreur suppression rôle '{}': {}", roleKey, e.getMessage(), e);
            throw new RuntimeException(
                    "Impossible de supprimer le rôle dans Keycloak : " + e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String keycloakId) {
        try {
            realmResource().users().get(keycloakId)
                    .executeActionsEmail(List.of("UPDATE_PASSWORD"));
            log.info("Email reset envoyé à: {}", keycloakId);
        } catch (Exception e) {
            log.error("Échec envoi email reset pour {}: {} - {}",
                    keycloakId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    public List<String> getAvailableRoles() {
        return realmResource().roles().list().stream()
                .map(RoleRepresentation::getName)
                .filter(name -> !SYSTEM_ROLES.contains(name))
                .sorted()
                .toList();
    }

    public List<String> getUserRoles(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) return Collections.emptyList();
        try {
            return realmResource().users().get(keycloakId)
                    .roles().realmLevel().listEffective().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !SYSTEM_ROLES.contains(name))
                    .toList();
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.warn("Utilisateur Keycloak introuvable : {}", keycloakId);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Erreur lecture rôles pour {}: {}", keycloakId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void setUserEnabled(String keycloakId, boolean enabled) {
        try {
            UserResource userResource = realmResource().users().get(keycloakId);
            UserRepresentation user   = userResource.toRepresentation();
            user.setEnabled(enabled);
            userResource.update(user);
            log.info("User Keycloak {} {}", keycloakId, enabled ? "activé" : "désactivé");
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new RuntimeException("Utilisateur introuvable dans Keycloak : " + keycloakId);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de modifier l'état : " + e.getMessage());
        }
    }

    public void updateUserProfile(String keycloakId, String firstName,
                                  String lastName, String email) {
        try {
            UserResource        userResource = realmResource().users().get(keycloakId);
            UserRepresentation  user         = userResource.toRepresentation();

            if (firstName != null) user.setFirstName(firstName);
            if (lastName  != null) user.setLastName(lastName);
            if (email     != null) { user.setEmail(email); user.setEmailVerified(true); }

            userResource.update(user);
            log.info("Profil mis à jour pour: {}", keycloakId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new RuntimeException("Utilisateur introuvable dans Keycloak : " + keycloakId);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de mettre à jour le profil : " + e.getMessage());
        }
    }

    /**
     * Vérifie le mot de passe actuel d'un utilisateur via un flux Direct Access
     * Grant Keycloak (grant_type=password) — n'émet aucun token utilisable,
     * ne fait que valider les identifiants avant d'autoriser un changement
     * de mot de passe.
     */
    public boolean verifyCurrentPassword(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            return false;
        }
        try (Keycloak client = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.PASSWORD)
                .username(username)
                .password(password)
                .build()) {
            client.tokenManager().getAccessToken();
            return true;
        } catch (Exception e) {
            log.warn("Échec de vérification du mot de passe actuel pour {} : {}",
                    username, e.getMessage());
            return false;
        }
    }

    public void changePassword(String keycloakId, String newPassword) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);
            realmResource().users().get(keycloakId).resetPassword(credential);
            log.info("Mot de passe changé pour: {}", keycloakId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new RuntimeException("Utilisateur introuvable dans Keycloak : " + keycloakId);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de changer le mot de passe : " + e.getMessage());
        }
    }

    private List<RoleRepresentation> resolveRolesBulk(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return List.of();

        try {

            Map<String, RoleRepresentation> allRoles = realmResource().roles().list()
                    .stream()
                    .collect(Collectors.toMap(
                            RoleRepresentation::getName,
                            r -> r
                    ));


            List<RoleRepresentation> resolved = roleNames.stream()
                    .map(name -> {
                        RoleRepresentation r = allRoles.get(name);
                        if (r == null) log.warn("Rôle Keycloak introuvable : {}", name);
                        return r;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            log.debug("resolveRolesBulk : {} rôles demandés → {} résolus en 1 appel HTTP",
                    roleNames.size(), resolved.size());

            return resolved;

        } catch (Exception e) {
            log.error("Erreur resolveRolesBulk : {}", e.getMessage());
            return List.of();
        }
    }
}