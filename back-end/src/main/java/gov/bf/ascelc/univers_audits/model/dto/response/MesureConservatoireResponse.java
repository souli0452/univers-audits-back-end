package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesureConservatoireResponse {

    private UUID id;
    private UUID investigationId;
    private String description;
    private UUID takenById;
    private String takenByNom;
    private Instant takenAt;
}
