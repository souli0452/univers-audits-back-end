package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteAvancementResponse {
    private UUID id;
    private Instant noteAt;
    private String agentNom;
    private String contenu;
}
