package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenirSeanceRequest {

    @Size(max = 5000)
    private String procesVerbal;
}
