package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransmissionAutoriteRequest {
    @NotBlank(message = "L'autorité destinataire est obligatoire")
    private String autoriteDestinataire;
}
