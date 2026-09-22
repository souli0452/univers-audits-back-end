package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.TypeDepassement;
import gov.bf.ascelc.univers_audits.model.dto.response.ActeurDepassementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DepassementItemResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.SeanceCtadpDossierRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.service.StatistiqueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatistiqueServiceImpl implements StatistiqueService {

    private final DossierRepository       dossierRepository;
    private final InvestigationRepository investigationRepository;
    private final NotificationRepository  notificationRepository;
    private final SeanceCtadpDossierRepository seanceCtadpDossierRepository;
    private final DecisionCGERepository        decisionCgeRepository;
    private final TargetedPartyRepository      targetedPartyRepository;
    private final DemandeDocumentsRepository   demandeDocumentsRepository;

    private static final ZoneId OUAGA_TZ = ZoneId.of("Africa/Ouagadougou");
    private static final List<DossierStatus> STATUTS_RECEVABLES = List.of(
            DossierStatus.RECEVABLE,
            DossierStatus.EN_INVESTIGATION,
            DossierStatus.RAPPORT_PRODUIT,
            DossierStatus.DECISION_RENDUE,
            DossierStatus.CLOS
    );

    @Override
    public List<ActeurDepassementResponse> getDepassementsParActeur() {
        log.info("[Stats] Calcul du tableau des dépassements par acteur");

        Instant now = Instant.now();
        Map<UUID, Agent> agents = new LinkedHashMap<>();
        Map<UUID, List<DepassementItemResponse>> itemsByAgent = new LinkedHashMap<>();

        for (Dossier dossier : dossierRepository.findOverdueAcknowledgments(now)) {
            addDepassement(agents, itemsByAgent, dossier.getAgentInCharge(),
                    DepassementItemResponse.builder()
                            .dossierId(dossier.getId())
                            .numero(dossier.getNumber())
                            .type(TypeDepassement.ACCUSE_RECEPTION)
                            .echeance(dossier.getAcknowledgmentDeadline())
                            .joursDeRetard(joursDeRetard(dossier.getAcknowledgmentDeadline(), now))
                            .build());
        }

        for (Dossier dossier : dossierRepository.findOverdueComplementRequests(now)) {
            addDepassement(agents, itemsByAgent, dossier.getAgentInCharge(),
                    DepassementItemResponse.builder()
                            .dossierId(dossier.getId())
                            .numero(dossier.getNumber())
                            .type(TypeDepassement.COMPLEMENT)
                            .echeance(dossier.getAdditionalInfoDeadline())
                            .joursDeRetard(joursDeRetard(dossier.getAdditionalInfoDeadline(), now))
                            .build());
        }

        for (Investigation investigation : investigationRepository.findOverdue(now)) {
            Dossier dossier = investigation.getDossier();
            Instant echeance = investigation.getExtendedDeadline() != null
                    ? investigation.getExtendedDeadline()
                    : investigation.getPlannedEndDate();
            addDepassement(agents, itemsByAgent, dossier.getAgentInCharge(),
                    DepassementItemResponse.builder()
                            .dossierId(dossier.getId())
                            .numero(dossier.getNumber())
                            .type(TypeDepassement.INVESTIGATION)
                            .echeance(echeance)
                            .joursDeRetard(joursDeRetard(echeance, now))
                            .build());
        }

        for (DemandeDocuments demande : demandeDocumentsRepository.findOverdue(now)) {
            Dossier dossier = demande.getInvestigation().getDossier();
            addDepassement(agents, itemsByAgent, dossier.getAgentInCharge(),
                    DepassementItemResponse.builder()
                            .dossierId(dossier.getId())
                            .numero(dossier.getNumber())
                            .type(TypeDepassement.DEMANDE_DOCUMENTS)
                            .echeance(demande.getDeadline())
                            .joursDeRetard(joursDeRetard(demande.getDeadline(), now))
                            .build());
        }

        List<ActeurDepassementResponse> result = new ArrayList<>();
        for (Map.Entry<UUID, List<DepassementItemResponse>> entry : itemsByAgent.entrySet()) {
            Agent agent = agents.get(entry.getKey());
            result.add(ActeurDepassementResponse.builder()
                    .agentId(agent.getId())
                    .matricule(agent.getMatricule())
                    .nomComplet(agent.getNomComplet())
                    .departementLibelle(agent.getDepartement() != null
                            ? agent.getDepartement().getLibelle() : null)
                    .dossiersEnDepassement(entry.getValue())
                    .build());
        }
        result.sort(Comparator.comparing(ActeurDepassementResponse::getNomComplet));
        return result;
    }

    private void addDepassement(Map<UUID, Agent> agents,
                                 Map<UUID, List<DepassementItemResponse>> itemsByAgent,
                                 Agent agent, DepassementItemResponse item) {
        if (agent == null) {
            return;
        }
        agents.putIfAbsent(agent.getId(), agent);
        itemsByAgent.computeIfAbsent(agent.getId(), id -> new ArrayList<>()).add(item);
    }

    private long joursDeRetard(Instant echeance, Instant now) {
        if (echeance == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(echeance, now));
    }

    @Override
    public StatistiqueResponse getDashboard(Instant start, Instant end) {
        log.info("[Stats] Calcul tableau de bord — {} → {}", start, end);

        long total = dossierRepository.countByReceptionDateBetween(start, end);

        Map<String, Long> byStatus = buildMap(
                dossierRepository.countByStatusAndReceptionDateBetween(start, end));

        Map<String, Long> byMode = buildMap(
                dossierRepository.countBySubmissionModeBetween(start, end));

        Map<String, Long> byType = buildMap(
                dossierRepository.countByTypeBetween(start, end));

        var totalLoss = dossierRepository.sumEstimatedLossBetween(start, end);
        long investigatedCount = investigationRepository
                .countByDossierReceptionDateBetween(start, end);

        long reportsProduced =
                byStatus.getOrDefault("RAPPORT_PRODUIT",  0L)
                        + byStatus.getOrDefault("DECISION_RENDUE",  0L)
                        + byStatus.getOrDefault("CLOS",             0L);
        long referredToJustice = investigationRepository
                .countByOutcomeBetween("JUDICIAL_REFERRAL", start, end);

        long inadmissible = byStatus.getOrDefault("IRRECEVABLE", 0L);
        long transferred  = byStatus.getOrDefault("TRANSFERE",   0L);
        long recevablesTotal = dossierRepository
                .countByStatusInAndReceptionDateBetween(STATUTS_RECEVABLES, start, end);

        long examined = recevablesTotal + inadmissible;
        double admissibilityRate = examined > 0
                ? (recevablesTotal * 100.0) / examined
                : 0.0;

        log.info("[Stats] Taux recevabilité : {}/{} = {}%",
                recevablesTotal, examined, String.format("%.1f", admissibilityRate));

        double investigationCoverageRate = total > 0
                ? (investigatedCount * 100.0) / total
                : 0.0;

        double reportProductionRate = investigatedCount > 0
                ? (reportsProduced * 100.0) / investigatedCount
                : 0.0;

        long acknowledgmentSentCount = notificationRepository
                .countByTypeAndStatusAndDossierReceptionDateBetween(
                        gov.bf.ascelc.univers_audits.enums.NotificationType.ACKNOWLEDGMENT_B5,
                        gov.bf.ascelc.univers_audits.enums.NotificationStatus.SENT,
                        start, end);
        double acknowledgmentCoverageRate = total > 0
                ? (acknowledgmentSentCount * 100.0) / total
                : 0.0;


        Double avgRegistrationDays = dossierRepository
                .avgRegistrationDelayInDays(start, end);

        Double avgInvestigationDays = investigationRepository
                .avgDurationInDays(start, end);

        Double avgDeiDays = investigationRepository.avgDeiApprovalDays(start, end);
        Double avgCgeDays = investigationRepository.avgCgeApprovalDays(start, end);

        long overdueAck  = dossierRepository
                .countOverdueAcknowledgments(Instant.now());
        long overdueInv  = investigationRepository
                .countOverdue(Instant.now());
        long overdueComp = dossierRepository
                .countOverdueComplementRequests(Instant.now());

        Map<String, Long> byCtadpRecommandation = buildMap(
                seanceCtadpDossierRepository.countByRecommandationBetween(start, end));

        Map<String, Long> byCgeDecision = buildMap(
                decisionCgeRepository.countByDecisionBetween(start, end));

        Map<String, Long> byTargetedPartyType = buildMap(
                targetedPartyRepository.countByPartyTypeBetween(start, end));

        Map<String, Long> byInvestigationOutcome = buildMap(
                investigationRepository.countByOutcomeGrouped(start, end));

        Double avgOpportunityStudy = dossierRepository
                .avgOpportunityStudyDelayInDays(start, end);

        Double avgLegalAdvisorDays = investigationRepository
                .avgLegalAdvisorApprovalDays(start, end);

        Double avgCgeaDays = investigationRepository
                .avgCgeaApprovalDays(start, end);

        Double avgPlanActionsDays = investigationRepository
                .avgPlanActionsSubmissionDays(start, end);

        List<StatistiqueResponse.MonthlyCount> monthlyTrend =
                buildMonthlyTrend(start, end);

        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("dd/MM/yyyy").withZone(OUAGA_TZ);
        String period = fmt.format(start) + " — " + fmt.format(end);

        return StatistiqueResponse.builder()
                .period(period)
                .generatedAt(Instant.now().toString())
                .totalDossiers(total)
                .countByStatus(byStatus)
                .monthlyTrend(monthlyTrend)
                .countBySubmissionMode(byMode)
                .countByType(byType)
                .inadmissibleCount(inadmissible)
                .investigatedCount(investigatedCount)
                .transferredCount(transferred)
                .closedWithoutInvestigationCount(
                        Math.max(0, inadmissible - investigatedCount))
                .totalEstimatedLoss(totalLoss)
                .inProgressInvestigations(
                        byStatus.getOrDefault("EN_INVESTIGATION", 0L))
                .reportsProduced(reportsProduced)
                .referredToJustice(referredToJustice)
                .unfoundedWithoutInvestigation(inadmissible)
                .unfoundedAfterInvestigation(
                        investigationRepository.countByOutcomeBetween("ARCHIVED", start, end))
                .admissibilityRate(admissibilityRate)
                .investigationCoverageRate(investigationCoverageRate)
                .reportProductionRate(reportProductionRate)
                .acknowledgmentCoverageRate(acknowledgmentCoverageRate)
                .avgRegistrationDelayDays(avgRegistrationDays)
                .avgInvestigationDurationDays(avgInvestigationDays)
                .avgDeiApprovalDays(avgDeiDays)
                .avgCgeApprovalDays(avgCgeDays)
                .countByCtadpRecommandation(byCtadpRecommandation)
                .countByCgeDecision(byCgeDecision)
                .countByTargetedPartyType(byTargetedPartyType)
                .countByInvestigationOutcome(byInvestigationOutcome)
                .avgOpportunityStudyDays(avgOpportunityStudy)
                // avgAcknowledgmentDays reste null : aucun horodatage d'emission du recepisse
                // n'existe dans le modele (Dossier.acknowledgmentDeadline est une echeance,
                // pas un horodatage d'emission) — voir spec
                // 2026-08-19-statistiques-priorisations-parties-delais-design.md
                .avgLegalAdvisorApprovalDays(avgLegalAdvisorDays)
                .avgCgeaApprovalDays(avgCgeaDays)
                .avgPlanActionsSubmissionDays(avgPlanActionsDays)
                .overdueAcknowledgments(overdueAck)
                .overdueInvestigations(overdueInv)
                .overdueComplements(overdueComp)
                .build();
    }


    @Override
    public StatistiqueResponse getQuarterlyStats(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        int endMonth   = startMonth + 2;

        Instant start = YearMonth.of(year, startMonth)
                .atDay(1).atStartOfDay(OUAGA_TZ).toInstant();
        Instant end = endMonth == 12
                ? YearMonth.of(year + 1, 1)
                .atDay(1).atStartOfDay(OUAGA_TZ).toInstant()
                : YearMonth.of(year, endMonth + 1)
                .atDay(1).atStartOfDay(OUAGA_TZ).toInstant();

        log.info("[Stats] Trimestriel T{} {} : {} → {}", quarter, year, start, end);
        return getDashboard(start, end);
    }


    @Override
    public StatistiqueResponse getAnnualStats(int year) {
        Instant start = YearMonth.of(year, 1)
                .atDay(1).atStartOfDay(OUAGA_TZ).toInstant();
        Instant end = YearMonth.of(year + 1, 1)
                .atDay(1).atStartOfDay(OUAGA_TZ).toInstant();

        log.info("[Stats] Annuel {} : {} → {}", year, start, end);
        return getDashboard(start, end);
    }


    @Override
    public Map<String, Object> getPublicStats() {
        long total = dossierRepository.count();

        long nouveaux = dossierRepository.countByStatusIn(List.of(
                DossierStatus.SOUMIS,
                DossierStatus.RECU
        ));
        long enCours = dossierRepository.countByStatusIn(List.of(
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                DossierStatus.EN_ATTENTE_COMPLEMENT,
                DossierStatus.EN_REVUE_CTADP,
                DossierStatus.RECEVABLE,
                DossierStatus.EN_INVESTIGATION,
                DossierStatus.RAPPORT_PRODUIT
        ));
        long traites = dossierRepository.countByStatusIn(List.of(
                DossierStatus.DECISION_RENDUE,
                DossierStatus.CLOS,
                DossierStatus.CLASSE,
                DossierStatus.IRRECEVABLE
        ));

        return Map.of(
                "totalDossiers",    total,
                "dossiersNouveaux", nouveaux,
                "dossiersEnCours",  enCours,
                "dossiersTraites",  traites,
                "confidentiel",     "100%",
                "delaiJours",       7
        );
    }

    private List<StatistiqueResponse.MonthlyCount> buildMonthlyTrend(
            Instant start, Instant end) {
        List<StatistiqueResponse.MonthlyCount> trend = new ArrayList<>();
        try {
            YearMonth current = YearMonth.from(start.atZone(OUAGA_TZ));
            YearMonth last    = YearMonth.from(end.atZone(OUAGA_TZ));

            while (!current.isAfter(last)) {
                Instant mStart = current.atDay(1)
                        .atStartOfDay(OUAGA_TZ).toInstant();
                Instant mEnd   = current.atEndOfMonth()
                        .atTime(23, 59, 59).atZone(OUAGA_TZ).toInstant();

                long count = dossierRepository
                        .countByReceptionDateBetween(mStart, mEnd);

                long investigations = investigationRepository
                        .countByDossierReceptionDateBetween(mStart, mEnd);

                trend.add(StatistiqueResponse.MonthlyCount.builder()
                        .month(current.toString())
                        .count(count)
                        .investigations(investigations)
                        .build());

                current = current.plusMonths(1);
            }
        } catch (Exception e) {
            log.warn("[Stats] Erreur construction tendance mensuelle : {}", e.getMessage());
        }
        return trend;
    }

    private Map<String, Long> buildMap(List<Object[]> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.put(row[0].toString(), (Long) row[1]);
            }
        }
        return result;
    }
}