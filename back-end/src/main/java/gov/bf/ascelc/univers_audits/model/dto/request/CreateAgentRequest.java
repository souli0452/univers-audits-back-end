// CreateAgentRequest.java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateAgentRequest(
        @NotBlank String matricule,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        String phoneNumber,
        String grade,
        List<String> keycloakRoles   // noms des rôles Keycloak cochés
) {}
