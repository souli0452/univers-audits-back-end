package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class StatistiqueResponse {

    private String period;
    private String generatedAt;

    /** Nombre total de dossiers reçus sur la période */
    private long totalDossiers;
    /** Répartition par statut — clé: DossierStatus, valeur: count */
    private Map<String, Long> countByStatus;
    /** Évolution mensuelle — pour le graphique courbe */
    private List<MonthlyCount> monthlyTrend;

    private Map<String, Long> countBySubmissionMode;

    /** Dossiers irrecevables — insuffisance d'information */
    private long inadmissibleCount;
    /** Dossiers ayant déclenché une investigation */
    private long investigatedCount;
    /** Dossiers transférés à une autre institution */
    private long transferredCount;
    /** Dossiers clôturés sans investigation */
    private long closedWithoutInvestigationCount;

    /** Répartition par type (COMPLAINT, DENUNCIATION, AUTO_REFERRAL...) */
    private Map<String, Long> countByType;

    /** Somme totale des préjudices estimés signalés */
    private BigDecimal totalEstimatedLoss;


    /** Dossiers en cours d'investigation */
    private long inProgressInvestigations;
    /** Dossiers avec rapport d'investigation produit */
    private long reportsProduced;
    /** Dossiers avec poursuites judiciaires (renvoyés au Parquet) */
    private long referredToJustice;

    /** Dossiers non fondés sans investigation */
    private long unfoundedWithoutInvestigation;
    /** Dossiers non fondés après investigation */
    private long unfoundedAfterInvestigation;
    /** Taux de recevabilité en pourcentage */
    private double admissibilityRate;


    private Double avgRegistrationDelayDays;


    private Double avgOpportunityStudyDays;


    private Double avgAcknowledgmentDays;


    private Double avgInvestigationDurationDays;


    private Double avgDeiApprovalDays;


    private Double avgCgeApprovalDays;


    private long overdueAcknowledgments;
    private long overdueInvestigations;
    private long overdueComplements;

    /** Répartition des recommandations CTADP — clé: RecommandationCtadp, valeur: count */
    private Map<String, Long> countByCtadpRecommandation;

    /** Répartition des décisions CGE — clé: RecommandationCtadp, valeur: count */
    private Map<String, Long> countByCgeDecision;

    /** Répartition des parties visées par type — clé: PartyType, valeur: count */
    private Map<String, Long> countByTargetedPartyType;

    /** Répartition complète des résultats d'investigation — clé: InvestigationOutcome, valeur: count */
    private Map<String, Long> countByInvestigationOutcome;

    private Double avgLegalAdvisorApprovalDays;

    private Double avgCgeaApprovalDays;

    private Double avgPlanActionsSubmissionDays;

    /** Part des dossiers reçus ayant déclenché une investigation, en % */
    private double investigationCoverageRate;

    /** Part des investigations ayant produit un rapport, en % */
    private double reportProductionRate;

    /** Part des dossiers reçus dont l'accusé de réception (B5) a été effectivement envoyé, en % */
    private double acknowledgmentCoverageRate;


    @Data
    @Builder
    public static class MonthlyCount {
        /** Format : "2026-01" */
        private String month;
        /** Nombre de dossiers reçus ce mois */
        private long   count;
        /** Nombre d'investigations ouvertes ce mois */
        private long   investigations;
    }
}