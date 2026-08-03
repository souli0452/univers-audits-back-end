package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceCtadpCreateRequest {

    @NotNull(message = "La date de la séance est obligatoire")
    private Instant dateSeance;

    @Size(max = 2000)
    private String participants;
}
