package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteRecommandationsResponse {
    private UUID id;
    private UUID rapportEnqueteId;
    private String contenu;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
