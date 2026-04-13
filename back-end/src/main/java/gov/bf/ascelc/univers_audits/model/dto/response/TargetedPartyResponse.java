package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AllegedRole;
import gov.bf.ascelc.univers_audits.enums.PartyType;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetedPartyResponse {
    private UUID id;
    private PartyType partyType;
    private String firstName;
    private String name;
    private String position;
    private String institution;
    private String organization;
    private String address;
    private String phoneNumber;
    private String email;
    private String relationWithDeclarant;
    private AllegedRole allegedRole;
    private String displayName;
}