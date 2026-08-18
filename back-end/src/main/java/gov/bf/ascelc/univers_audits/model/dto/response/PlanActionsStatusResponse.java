package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanActionsStatusResponse {
    private UUID investigationId;
    private boolean exists;
    private Instant planActionsDueAt;
    private boolean planActionsOverdue;
    private UUID id;
    private String entiteControlee;
    private String contenu;
    private Instant submittedAt;
    private String receivedByNom;
    private List<NoteAvancementResponse> avancements;
}
