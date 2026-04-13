package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendDeadlineRequest {

    @NotNull(message = "La nouvelle date limite est obligatoire")
    private Instant newDeadline;

    @NotBlank(message = "La justification de l'extension est obligatoire")
    private String reason;
}