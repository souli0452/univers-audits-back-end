package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WitnessRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 100)
    private String profession;

    @Size(max = 20)
    private String phoneNumber;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 300)
    private String address;

    private String testimonyNature;

    @Size(max = 200)
    private String relationWithParties;

    private LocalDate interrogationDate;

    private Boolean consentToContact;

    private Boolean anonymous;
}