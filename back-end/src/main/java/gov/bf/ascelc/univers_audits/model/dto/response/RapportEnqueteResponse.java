package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportEnqueteResponse {
    private UUID id;
    private UUID investigationId;
    private String titre;
    private String introduction;
    private String methodologie;
    private String informationsCollectees;
    private String exposeFactuelAnomalies;
    private String quantificationPrejudice;
    private String reserves;
    private String conclusions;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
