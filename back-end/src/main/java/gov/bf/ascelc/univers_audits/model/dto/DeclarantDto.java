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
public class DeclarantDto extends AuditEntityDto {

    private String firstName;
    private String lastName;
    private Boolean anonymous;
    private String phoneNumber;
    private String email;
    private String profession;
    private String commune;
    private String address;


}
