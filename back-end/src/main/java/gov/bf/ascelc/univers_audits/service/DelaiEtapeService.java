package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import gov.bf.ascelc.univers_audits.model.entity.StatusHistory;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Délais des étapes du circuit de traitement d'un dossier (workflow PGPD_GU V3).
 *
 * <p>Seules les étapes dont le début et la fin correspondent à des dates déjà enregistrées sont suivies :
 * analyse du CGEA, convocation du comité, quitus du CGE, imputation du CGE, affectation du CGEA.
 * Une étape n'apparaît qu'une fois commencée. Les délais sont les paramètres « ETAPE_* » (jours ouvrables).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelaiEtapeService {

    static final String ANALYSE_CGEA      = "ETAPE_ANALYSE_CGEA";
    static final String CONVOCATION_CTADP = "ETAPE_CONVOCATION_CTADP";
    static final String QUITUS_CGE        = "ETAPE_QUITUS_CGE";
    static final String IMPUTATION_CGE    = "ETAPE_IMPUTATION_CGE";
    static final String AFFECTATION_CGEA  = "ETAPE_AFFECTATION_CGEA";

    private final DossierService dossierService;
    private final StatusHistoryRepository statusHistoryRepository;
    private final SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    private final DecisionCGERepository decisionCGERepository;
    private final FicheAffectationRepository ficheAffectationRepository;
    private final ParametreDelaiService parametreDelaiService;
    private final DelaiEtapeCalculator calculator;

    @Transactional(readOnly = true)
    public List<DelaiEtapeResponse> findByDossierId(UUID dossierId) {
        // findById applique le contrôle d'affectation/rôle et le masquage de confidentialité.
        DossierResponse dossier = dossierService.findById(dossierId);
        List<DelaiEtapeResponse> etapes = evaluer(dossierId, dossier.getReceptionDate(), Instant.now());

        // Dossier terminé : une étape restée ouverte n'a plus lieu d'être, on ne la signale pas en retard.
        if (dossier.getStatus() == DossierStatus.CLOS || dossier.getStatus() == DossierStatus.CLASSE) {
            return etapes.stream().filter(e -> e.getFin() != null).toList();
        }
        return etapes;
    }

    /**
     * Calcul des délais sans contrôle d'accès, pour un usage système (tâche planifiée d'alerte).
     * Ne pas exposer tel quel à un utilisateur : passer par {@link #findByDossierId(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<DelaiEtapeResponse> evaluer(UUID dossierId, Instant receptionDate, Instant maintenant) {
        List<StatusHistory> historique = statusHistoryRepository.findByDossierIdOrderByChangedAtAsc(dossierId);
        List<SeanceCtadpDossier> seances = seanceCtadpDossierRepository.findByDossierIdOrderByCreatedAtAsc(dossierId);
        DecisionCGE decision = decisionCGERepository.findByDossierId(dossierId).orElse(null);
        FicheAffectation fiche = ficheAffectationRepository.findByDossierId(dossierId).orElse(null);

        List<DelaiEtapeResponse> etapes = new ArrayList<>();

        Instant dateDecision = decision != null ? decision.getDateDecision() : null;

        // Étape 5 — analyse du CGEA : du dossier reçu au démarrage de l'étude d'opportunité. Si cette
        // transition n'est pas enregistrée, un jalon postérieur prouve que l'étape est passée.
        ajouter(etapes, ANALYSE_CGEA, "Analyse et transmission au Conseiller juridique", "CGEA",
                receptionDate,
                premier(premiereTransition(historique, DossierStatus.EN_ETUDE_OPPORTUNITE),
                        premiereTransition(historique, DossierStatus.EN_REVUE_CTADP),
                        dateDecision),
                maintenant);

        // Étape 7 — convocation : de l'envoi au comité à l'inscription du dossier à une séance. Un dossier
        // tranché par le CGE sans séance enregistrée n'a plus de convocation à attendre.
        ajouter(etapes, CONVOCATION_CTADP, "Convocation du comité", "CGEA",
                premiereTransition(historique, DossierStatus.EN_REVUE_CTADP),
                premier(seances.isEmpty() ? null : seances.get(0).getCreatedAt(), dateDecision),
                maintenant);

        // Étape 9 — quitus : de la séance tenue à la décision du CGE.
        Instant seanceTenue = seances.stream()
                .map(SeanceCtadpDossier::getSeanceCtadp)
                .filter(s -> s.getStatut() == StatutSeanceCtadp.TENUE)
                .map(s -> s.getDateSeance())
                .findFirst().orElse(null);
        ajouter(etapes, QUITUS_CGE, "Quitus du CGE sur l'avis du comité", "CGE",
                seanceTenue, decision != null ? decision.getDateDecision() : null, maintenant);

        // Étape 12 — imputation : du dossier retenu pour investigation à la décision d'affectation du CGE.
        boolean retenu = decision != null && decision.getDecision() == RecommandationCtadp.VALIDATION_INVESTIGATION;
        ajouter(etapes, IMPUTATION_CGE, "Imputation du dossier retenu", "CGE",
                retenu ? decision.getDateDecision() : null,
                fiche != null ? fiche.getDateDecisionCge() : null,
                maintenant);

        // Étape 13 — affectation : de la décision du CGE à l'imputation par le CGEA.
        ajouter(etapes, AFFECTATION_CGEA, "Affectation du dossier", "CGEA",
                fiche != null ? fiche.getDateDecisionCge() : null,
                fiche != null ? fiche.getDateImputation() : null,
                maintenant);

        return etapes;
    }

    private void ajouter(List<DelaiEtapeResponse> etapes, String code, String libelle, String acteur,
                         Instant debut, Instant fin, Instant maintenant) {
        if (debut == null) return; // étape pas encore commencée

        int delaiJours;
        boolean joursOuvrables;
        try {
            delaiJours = parametreDelaiService.resolveDelaiJours(code);
            joursOuvrables = parametreDelaiService.resolveJoursOuvrables(code);
        } catch (ResourceNotFoundException e) {
            log.debug("[DelaiEtape] Étape {} ignorée : {}", code, e.getMessage());
            return; // paramètre désactivé ou sans valeur : l'étape n'est pas suivie
        }

        etapes.add(calculator.evaluer(code, libelle, acteur, debut, fin, delaiJours, joursOuvrables, maintenant));
    }

    /** Le plus ancien des instants non nuls, ou null s'il n'y en a aucun. */
    private static Instant premier(Instant... instants) {
        Instant resultat = null;
        for (Instant i : instants) {
            if (i != null && (resultat == null || i.isBefore(resultat))) resultat = i;
        }
        return resultat;
    }

    private Instant premiereTransition(List<StatusHistory> historique, DossierStatus statut) {
        return historique.stream()
                .filter(h -> h.getNewStatus() == statut)
                .map(StatusHistory::getChangedAt)
                .findFirst().orElse(null);
    }
}
