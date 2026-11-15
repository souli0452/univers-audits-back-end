package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.service.CurrentUserAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nom d'interlocuteur d'une audition tel que l'utilisateur a le droit de le voir. Quand le nom d'un lanceur
 * d'alerte protégé est renvoyé en clair à un agent habilité, la consultation est écrite au journal d'audit :
 * une entrée par appel de {@link #mask}, une seule entrée pour toute une liste avec {@link #maskAll}.
 */
@Component
@RequiredArgsConstructor
public class AuditionDisplayNameMasker {

    private static final String ACTION_CONSULTATION = "CONSULTER_IDENTITE_LANCEUR_ALERTE";

    private final SecurityUtils securityUtils;
    private final CurrentUserAuditor auditor;

    /** Nom pour une audition seule ; trace la consultation si une identité protégée est montrée. */
    public String mask(Audition audition) {
        Resultat resultat = resoudre(audition);
        if (resultat.identiteProtegeeMontree()) {
            tracer(List.of(audition));
        }
        return resultat.nom();
    }

    /** Noms d'une liste d'auditions, dans le même ordre ; une seule trace pour toute la liste. */
    public List<String> maskAll(Collection<Audition> auditions) {
        List<String> noms = new ArrayList<>();
        List<Audition> montrees = new ArrayList<>();
        for (Audition audition : auditions) {
            Resultat resultat = resoudre(audition);
            noms.add(resultat.nom());
            if (resultat.identiteProtegeeMontree()) {
                montrees.add(audition);
            }
        }
        if (!montrees.isEmpty()) {
            tracer(montrees);
        }
        return noms;
    }

    private Resultat resoudre(Audition audition) {
        String raw = audition.getIntervieweeDisplayName();
        if (audition.getIntervieweeType() != IntervieweeType.DECLARANT) {
            return new Resultat(raw, false);
        }
        Dossier dossier = audition.getInvestigation().getDossier();
        Declarant declarant = dossier.getDeclarant();
        boolean protege = declarant != null && Boolean.TRUE.equals(declarant.getProtectionRequested());
        boolean habilite = protege && securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER);
        if (protege && !habilite) {
            raw = "Lanceur d'alerte protégé";
        }
        if (Boolean.TRUE.equals(dossier.getAnonymous())) {
            return new Resultat("Déclarant anonyme", false);
        }
        return new Resultat(raw, habilite);
    }

    private void tracer(List<Audition> montrees) {
        String dossiers = montrees.stream()
                .map(a -> a.getInvestigation().getDossier().getNumber())
                .filter(n -> n != null)
                .distinct().limit(5)
                .collect(Collectors.joining(", "));
        String entityId = montrees.size() == 1 && montrees.get(0).getId() != null
                ? montrees.get(0).getId().toString() : null;
        auditor.record(ACTION_CONSULTATION, "AUDITION", entityId,
                "Consultation de l'identité de " + montrees.size()
                        + " lanceur(s) d'alerte protégé(s) dans les auditions"
                        + (dossiers.isEmpty() ? "" : " — dossier(s) : " + dossiers));
    }

    private record Resultat(String nom, boolean identiteProtegeeMontree) {
    }
}
