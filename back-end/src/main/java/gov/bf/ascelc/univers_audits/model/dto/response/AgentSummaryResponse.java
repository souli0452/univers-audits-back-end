package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSummaryResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String matricule;
    private String departementLabel;
}