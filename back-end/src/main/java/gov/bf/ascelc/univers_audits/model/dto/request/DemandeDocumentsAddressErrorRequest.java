package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeDocumentsAddressErrorRequest {

    @NotBlank(message = "Le destinataire corrigé est obligatoire")
    @Size(max = 300)
    private String correctedRecipientLabel;
}
