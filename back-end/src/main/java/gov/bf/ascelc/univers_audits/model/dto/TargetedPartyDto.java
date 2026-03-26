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
public class TargetedPartyDto extends AuditEntityDto {

    private String name;
    private String position;
    private String institution;
    private String organization;
    private UUID dossierId;
}
