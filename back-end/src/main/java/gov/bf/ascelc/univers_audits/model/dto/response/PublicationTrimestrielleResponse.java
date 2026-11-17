package gov.bf.ascelc.univers_audits.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * « Situation des plaintes et dénonciations » d'un trimestre, d'un semestre ou d'une année, telle que publiée sur le site de l'ASCE-LC : huit
 * tableaux d'agrégats, sans aucune donnée personnelle.
 */
public record PublicationTrimestrielleResponse(
        int annee,
        String codePeriode,
        String periode,
        String genereLe,
        long total,
        List<Ligne> categories,
        List<Ligne> modesDeSaisine,
        List<Ligne> typologiesInstitutionnelles,
        List<Ligne> allegations,
        List<Ligne> traitement,
        BigDecimal enjeuxFinanciers,
        List<Ligne> recevabilite,
        List<Ligne> originesGeographiques,
        List<Phase> velocite,
        List<String> notes) {

    /** Une ligne d'un tableau : nombre de dossiers et part du total (en %, deux décimales). */
    public record Ligne(String code, String libelle, long nombre, double taux) {
    }

    /** Une phase du circuit de traitement, avec le délai moyen constaté quand l'application le mesure. */
    public record Phase(String code, String libelle, long nombre, Double delaiMoyenJours) {
    }
}
