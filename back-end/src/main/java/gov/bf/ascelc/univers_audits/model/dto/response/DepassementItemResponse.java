package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.TypeDepassement;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DepassementItemResponse {

    private UUID dossierId;
    private String numero;
    private TypeDepassement type;
    private Instant echeance;
    private long joursDeRetard;
}
