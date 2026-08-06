package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteTerrainScheduleRequest {

    @NotBlank(message = "Le lieu de la visite est obligatoire")
    @Size(max = 300)
    private String location;

    @NotNull(message = "La date de la visite est obligatoire")
    private Instant scheduledAt;
}
