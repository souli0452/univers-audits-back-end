package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.AllegedRole;
import gov.bf.ascelc.univers_audits.enums.PartyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetedPartyRequest {

    @NotNull(message = "Le type de partie est obligatoire")
    private PartyType partyType;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String name;

    @Size(max = 150)
    private String position;

    @Size(max = 200)
    private String institution;

    @Size(max = 200)
    private String organization;

    @Size(max = 300)
    private String address;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 150)
    private String email;

    @Size(max = 200)
    private String relationWithDeclarant;

    private AllegedRole allegedRole;
}