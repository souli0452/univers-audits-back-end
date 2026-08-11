package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganisationDetailRequest {

    @NotNull(message = "Le mode d'organisation du detail est obligatoire")
    private OrganisationDetail organisationDetail;
}
