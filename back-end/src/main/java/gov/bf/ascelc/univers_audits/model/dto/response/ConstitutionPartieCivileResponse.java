package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstitutionPartieCivileResponse {
    private UUID id;
    private UUID investigationId;
    private Instant constitueAt;
    private BigDecimal montantReclame;
    private String justification;
    private String constitueeParNom;
    private Instant submittedAt;
}
