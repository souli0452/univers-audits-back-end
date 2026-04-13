package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WitnessResponse {
    private UUID id;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private String address;
    private String profession;
    private String testimonyNature;
    private String relationWithParties;
    private LocalDate interrogationDate;
    private Boolean consentToContact;
    private Boolean anonymous;
    private String displayName;
}