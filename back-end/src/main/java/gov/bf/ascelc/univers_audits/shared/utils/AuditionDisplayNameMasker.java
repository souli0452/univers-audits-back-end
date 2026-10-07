package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditionDisplayNameMasker {

    private final SecurityUtils securityUtils;

    public String mask(Audition audition) {
        String raw = audition.getIntervieweeDisplayName();
        if (audition.getIntervieweeType() != IntervieweeType.DECLARANT) {
            return raw;
        }
        Dossier dossier = audition.getInvestigation().getDossier();
        Declarant declarant = dossier.getDeclarant();
        if (declarant != null
                && Boolean.TRUE.equals(declarant.getProtectionRequested())
                && !securityUtils.hasRole("CGE")
                && !securityUtils.hasRole("CGEA")) {
            raw = "Lanceur d'alerte protégé";
        }
        if (Boolean.TRUE.equals(dossier.getAnonymous())) {
            raw = "Déclarant anonyme";
        }
        return raw;
    }
}
