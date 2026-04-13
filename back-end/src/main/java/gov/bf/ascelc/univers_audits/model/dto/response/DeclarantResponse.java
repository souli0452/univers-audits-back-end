package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclarantResponse {

    private UUID id;
    private TypeDeclarant typeDeclarant;
    private String quality;
    private String firstName;
    private String lastName;
    private String organizationName;
    private String email;
    private String phoneNumber;
    private String address;
    private String commune;
    private String province;
    private String profession;
    private String displayName;
    private Boolean anonymous;
    private Boolean protectionRequested;
    private Boolean notificationsAccepted;
}