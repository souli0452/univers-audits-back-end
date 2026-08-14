package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequeteParquetResponse {
    private UUID id;
    private UUID investigationId;
    private String contenu;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
