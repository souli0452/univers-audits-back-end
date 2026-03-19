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
public class WitnessDto extends AuditEntityDto {

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String address;
    private String email;
    private String profession;
    private String interrogationDate;
}