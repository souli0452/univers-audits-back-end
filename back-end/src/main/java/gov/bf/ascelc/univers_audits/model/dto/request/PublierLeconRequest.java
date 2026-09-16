package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class PublierLeconRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 300, message = "Le titre ne doit pas dépasser 300 caractères")
    private String titre;

    @NotBlank(message = "Le résumé est obligatoire")
    private String resume;
}
