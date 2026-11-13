package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Cumuls de rôles qu'une même personne ne doit pas détenir, déduits de la fiche de workflow PGPD_GU V3 :
 * deux étapes qui se contrôlent l'une l'autre ne doivent pas être tenues par la même personne.
 *
 * <ul>
 *   <li>{@link Niveau#INTERDIT} : refusé à la création ou à la modification d'un agent ;</li>
 *   <li>{@link Niveau#A_SURVEILLER} : autorisé, avec un avertissement dans le formulaire.</li>
 * </ul>
 *
 * ADMIN_DDIC est un compte technique qui garde ses permissions métier (décision du 2026-11-12) : s'il fait partie
 * des rôles, les cumuls interdits ne bloquent plus et deviennent de simples avertissements.
 */
public final class RoleCombinationPolicy {

    public enum Niveau { INTERDIT, A_SURVEILLER }

    public record Regle(String roleA, String roleB, Niveau niveau, String raison) {
        boolean concerne(Set<String> roles) {
            return roles.contains(roleA) && roles.contains(roleB);
        }
    }

    public static final String ADMIN = "ADMIN_DDIC";

    private static final List<Regle> REGLES;

    static {
        List<Regle> r = new ArrayList<>();
        for (String decideur : List.of("CGE", "CGEA")) {
            // Enregistrement (étapes 2-3) puis analyse et décision (étapes 5, 9 à 13)
            r.add(interdit("AGENT_BRPD", decideur,
                    "Elle enregistrerait le dossier (étape 3) puis le déciderait (étapes 2, 5, 9 à 13)."));
            // Instruction (étapes 17 à 20) puis approbation et poursuites (étapes 22 à 24)
            r.add(interdit("CONTROLEUR_ETAT", decideur,
                    "Elle instruirait le dossier (étapes 17 à 20) puis approuverait son propre rapport (étapes 22 à 24)."));
        }
        r.add(interdit("CONTROLEUR_ETAT", "CONSEILLER_JURIDIQUE",
                "Elle rédigerait le rapport d'enquête (étape 20) puis le validerait juridiquement (étape 21)."));
        r.add(interdit("CGE", "CGEA",
                "Le CGE donne le quitus sur le travail du CGEA (étape 9) : une même personne approuverait deux fois."));
        r.add(interdit("MEMBRE_CTADP", "CGE",
                "Le CGE donne le quitus sur l'avis de la CTADP (étapes 8 et 9) : elle jugerait son propre avis."));
        // La communication ne doit voir que des statistiques sans données personnelles
        for (String metier : List.of("AGENT_BRPD", "CONSEILLER_JURIDIQUE", "MEMBRE_CTADP", "CGEA", "CGE", "CONTROLEUR_ETAT")) {
            r.add(interdit("DCP", metier,
                    "La DCP ne doit accéder qu'à des statistiques globales sans données personnelles (étape 34)."));
        }

        r.add(surveiller("MEMBRE_CTADP", "CGEA",
                "Le CGEA convoque la CTADP (étape 7) puis traite son avis (étapes 10 et 11)."));
        r.add(surveiller("MEMBRE_CTADP", "CONTROLEUR_ETAT",
                "La CTADP décide des suites (étape 8), puis la même personne instruirait le dossier."));
        r.add(surveiller("CONSEILLER_JURIDIQUE", "CGE",
                "Elle donnerait l'avis juridique (étape 6) puis déciderait."));
        r.add(surveiller("CONSEILLER_JURIDIQUE", "CGEA",
                "Elle donnerait l'avis juridique (étape 6) puis déciderait."));
        r.add(surveiller("AGENT_BRPD", "CONSEILLER_JURIDIQUE",
                "Cumul rare, à justifier : enregistrement et avis juridique sur le même dossier."));
        r.add(surveiller("AGENT_BRPD", "MEMBRE_CTADP",
                "Cumul rare, à justifier : enregistrement et recommandation sur le même dossier."));
        r.add(surveiller("AGENT_BRPD", "CONTROLEUR_ETAT",
                "Cumul rare, à justifier : enregistrement et instruction du même dossier."));
        REGLES = List.copyOf(r);
    }

    private RoleCombinationPolicy() {
    }

    private static Regle interdit(String a, String b, String raison) {
        return new Regle(a, b, Niveau.INTERDIT, raison);
    }

    private static Regle surveiller(String a, String b, String raison) {
        return new Regle(a, b, Niveau.A_SURVEILLER, raison);
    }

    /** Toutes les règles, telles que publiées au formulaire d'agent. */
    public static List<Regle> regles() {
        return REGLES;
    }

    /** Cumuls bloquants pour ces rôles (vide si ADMIN_DDIC en fait partie). */
    public static List<Regle> interdits(Collection<String> roles) {
        Set<String> set = Set.copyOf(roles);
        if (set.contains(ADMIN)) {
            return List.of();
        }
        return REGLES.stream()
                .filter(r -> r.niveau() == Niveau.INTERDIT && r.concerne(set))
                .toList();
    }

    /** Lève une BusinessException qui nomme chaque cumul interdit. */
    public static void verifier(Collection<String> roles) {
        if (roles == null || roles.size() < 2) {
            return;
        }
        List<Regle> interdits = interdits(roles);
        if (interdits.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder("Cumul de rôles interdit : ");
        for (int i = 0; i < interdits.size(); i++) {
            Regle r = interdits.get(i);
            if (i > 0) {
                message.append(" ; ");
            }
            message.append(r.roleA()).append(" + ").append(r.roleB()).append(" — ").append(r.raison());
        }
        throw new BusinessException(message.toString());
    }
}
