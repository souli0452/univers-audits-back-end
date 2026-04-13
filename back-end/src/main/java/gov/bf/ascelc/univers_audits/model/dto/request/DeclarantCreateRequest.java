package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclarantCreateRequest {

    private TypeDeclarant typeDeclarant;

    private String quality;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 200)
    private String organizationName;

    @Email(message = "Format email invalide")
    @Size(max = 150)
    private String email;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 300)
    private String address;

    @Size(max = 100)
    private String commune;

    @Size(max = 100)
    private String province;

    @Size(max = 100)
    private String profession;

    @Size(max = 200)
    private String socialMediaAccount;

    @Size(max = 50)
    private String idDocumentNumber;

    @Size(max = 30)
    private String idDocumentType;

    private Boolean anonymous;
    private Boolean protectionRequested;
    private Boolean dataProcessingConsent;
    private Boolean notificationsAccepted;
}