package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LeconAPartagerResponse {

    private UUID id;
    private String titre;
    private String resume;
    private String publieeParNom;
    private UUID investigationId;
    private Instant createdAt;
}
