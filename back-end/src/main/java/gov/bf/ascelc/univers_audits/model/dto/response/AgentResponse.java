package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private UUID id;
    private String matricule;
    private String firstName;
    private String lastName;
    private String email;
    private Boolean actif;
    private String grade;
    private String keycloakId;
    private Instant createdAt;
}
