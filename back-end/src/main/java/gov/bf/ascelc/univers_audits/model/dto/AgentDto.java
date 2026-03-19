package gov.bf.ascelc.univers_audits.model.dto;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntityDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class AgentDto extends AuditEntityDto {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String matricule;
    private String grade;
    private String service;

}
