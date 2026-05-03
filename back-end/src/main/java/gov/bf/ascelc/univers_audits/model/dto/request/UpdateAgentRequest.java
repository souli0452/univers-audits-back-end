package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.Email;
import java.util.List;

public record UpdateAgentRequest(
        String firstName,
        String lastName,
        @Email String email,
        String phoneNumber,
        String grade,
        List<String> keycloakRoles   // null = pas de changement de rôles
) {}