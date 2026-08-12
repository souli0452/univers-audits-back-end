package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportEnqueteRequest {
    private String titre;
    private String introduction;
    private String methodologie;
    private String informationsCollectees;
    private String exposeFactuelAnomalies;
    private String quantificationPrejudice;
    private String reserves;
    private String conclusions;
}
