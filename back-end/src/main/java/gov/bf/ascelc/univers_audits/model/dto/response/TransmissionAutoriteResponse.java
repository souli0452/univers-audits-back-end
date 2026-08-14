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
public class TransmissionAutoriteResponse {
    private UUID id;
    private UUID investigationId;
    private String autoriteDestinataire;
    private Instant transmittedAt;
    private String transmittedByNom;
    private Instant relanceDueAt;
    private Boolean relanceOverdue;
    private List<RelanceSuitesResponse> relances;
}
