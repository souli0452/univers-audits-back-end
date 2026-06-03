package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.StatistiqueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatistiqueServiceImpl implements StatistiqueService {

    private final DossierRepository       dossierRepository;
    private final InvestigationRepository investigationRepository;
    private final NotificationRepository  notificationRepository;

    private static final ZoneId OUAGA_TZ = ZoneId.of("Africa/Ouagadougou");
    private static final List<DossierStatus> STATUTS_RECEVABLES = List.of(
            DossierStatus.RECEVABLE,
            DossierStatus.EN_INVESTIGATION,
            DossierStatus.RAPPORT_PRODUIT,
            DossierStatus.DECISION_RENDUE,
            DossierStatus.CLOS
    );

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


        Double avgRegistrationDays = dossierRepository
                .avgRegistrationDelayInDays(start, end);

        Double avgInvestigationDays = investigationRepository
                .avgDurationInDays(start, end);

        Double avgDeiDays = investigationRepository.avgDeiApprovalDays(start, end);
        Double avgCgeDays = investigationRepository.avgCgeApprovalDays(start, end);

        long overdueAck  = dossierRepository
                .findOverdueAcknowledgments(Instant.now()).size();
        long overdueInv  = investigationRepository
                .findOverdue(Instant.now()).size();
        long overdueComp = dossierRepository
                .findOverdueComplementRequests(Instant.now()).size();

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
                .avgRegistrationDelayDays(avgRegistrationDays)
                .avgInvestigationDurationDays(avgInvestigationDays)
                .avgDeiApprovalDays(avgDeiDays)
                .avgCgeApprovalDays(avgCgeDays)
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