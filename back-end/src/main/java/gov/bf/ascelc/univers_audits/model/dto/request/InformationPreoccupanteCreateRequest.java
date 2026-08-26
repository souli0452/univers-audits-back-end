package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformationPreoccupanteCreateRequest {

    @NotBlank(message = "L'objet est obligatoire")
    @Size(max = 500, message = "L'objet ne doit pas dépasser 500 caractères")
    private String objet;

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 10000, message = "La description ne doit pas dépasser 10000 caractères")
    private String description;

    @NotNull(message = "La source est obligatoire")
    private AutoReferralSource source;

    @Size(max = 500, message = "La référence source ne doit pas dépasser 500 caractères")
    private String sourceReference;

    @NotNull(message = "La date de réception est obligatoire")
    private Instant dateReception;
}
