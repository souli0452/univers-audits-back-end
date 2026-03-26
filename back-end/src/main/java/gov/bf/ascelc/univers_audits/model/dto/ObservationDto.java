package gov.bf.ascelc.univers_audits.model.dto;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntityDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class ObservationDto extends AuditEntityDto {

    private String observationDate;
    private String content;
    private String type;
    private String author;
    private UUID dossierId;
}
