package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RattacherDossierRequest {

    @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
    private String commentaire;
}
