package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Calcul pur de l'échéance et du statut d'une étape ; aucune lecture en base. */
@Component
@RequiredArgsConstructor
public class DelaiEtapeCalculator {

    private static final long SEUIL_PROCHE_HEURES = 24;

    private final DeadlineCalculator deadlineCalculator;

    /**
     * @param debut          début de l'étape (non nul)
     * @param fin            fin de l'étape, ou null si elle est en cours
     * @param delaiJours     délai accordé
     * @param joursOuvrables vrai pour exclure week-ends et jours fériés
     */
    public DelaiEtapeResponse evaluer(String code, String libelle, String acteur,
                                      Instant debut, Instant fin,
                                      int delaiJours, boolean joursOuvrables, Instant maintenant) {
        Instant echeance = joursOuvrables
                ? deadlineCalculator.addBusinessDays(debut, delaiJours)
                : deadlineCalculator.addCalendarDays(debut, delaiJours);

        StatutDelaiEtape statut;
        Long heuresRestantes = null;
        if (fin != null) {
            statut = fin.isAfter(echeance) ? StatutDelaiEtape.TERMINE_EN_RETARD : StatutDelaiEtape.RESPECTE;
        } else {
            heuresRestantes = Duration.between(maintenant, echeance).toHours();
            if (maintenant.isAfter(echeance)) {
                statut = StatutDelaiEtape.DEPASSE;
            } else if (heuresRestantes < SEUIL_PROCHE_HEURES) {
                statut = StatutDelaiEtape.PROCHE;
            } else {
                statut = StatutDelaiEtape.EN_COURS;
            }
        }

        return DelaiEtapeResponse.builder()
                .code(code).libelle(libelle).acteur(acteur)
                .debut(debut).echeance(echeance).fin(fin)
                .delaiJours(delaiJours).joursOuvrables(joursOuvrables)
                .statut(statut).heuresRestantes(heuresRestantes)
                .build();
    }
}
