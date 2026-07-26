package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeDocumentsCreateRequest {

    @NotBlank(message = "Le destinataire est obligatoire")
    @Size(max = 300)
    private String recipientLabel;

    @NotBlank(message = "La description des documents demandés est obligatoire")
    private String documentsRequested;
}
