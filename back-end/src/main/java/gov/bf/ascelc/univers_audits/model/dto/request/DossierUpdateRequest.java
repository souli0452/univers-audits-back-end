package gov.bf.ascelc.univers_audits.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierUpdateRequest {

    @NotNull(message = "La version est obligatoire pour la mise à jour")
    private Long version;

    @Size(max = 500)
    private String object;

    @Size(max = 10000)
    private String description;

    @Size(max = 5000)
    private String motifs;

    @Size(max = 300)
    private String incidentLocation;

    @Size(max = 50)
    private String numeroCourrier;

    @Size(max = 200)
    private String incidentPeriod;

    private BigDecimal estimatedLoss;

    @JsonProperty("isConfidential")
    private Boolean isConfidential;

    private String transferInstitution;

    /** Nature choisie par l'agent parmi plainte, dénonciation, plainte-dénonciation, demande d'intervention. */
    private gov.bf.ascelc.univers_audits.enums.TypeSaisine natureSaisineChoisie;

    /** Type de l'institution mise en cause, que seul l'agent qualifie. */
    private gov.bf.ascelc.univers_audits.enums.TypologieInstitutionnelle typologieInstitutionnelle;


    public Boolean getIsConfidential() {
        return isConfidential;
    }

    public void setIsConfidential(Boolean isConfidential) {
        this.isConfidential = isConfidential;
    }
}