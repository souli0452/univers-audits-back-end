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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    @Override
    public StatistiqueResponse getDashboard(Instant start, Instant end) {
        log.info("Calcul tableau de bord — {} → {}", start, end);

        long total        = dossierRepository.countByReceptionDateBetween(start, end);
        Map<String, Long> byStatus = buildMap(dossierRepository.countByStatus());
        Map<String, Long> byMode   = buildMap(dossierRepository.countBySubmissionModeBetween(start, end));
        Map<String, Long> byType   = buildMap(dossierRepository.countByTypeBetween(start, end));

        var    totalLoss            = dossierRepository.sumEstimatedLossBetween(start, end);
        Double avgProcessingSeconds = dossierRepository.avgProcessingTimeInSeconds(start, end);
        Double avgProcessingDays    = avgProcessingSeconds != null ? avgProcessingSeconds / 86400.0 : null;
        Double avgInvestigationDays = investigationRepository.avgDurationInDays(start, end);

        long overdueAck = dossierRepository.findOverdueAcknowledgments(Instant.now()).size();
        long overdueInv = investigationRepository.findOverdue(Instant.now()).size();

        long recevables = dossierRepository.countByStatusAndReceptionDateBetween(
                DossierStatus.RECEVABLE, start, end
        );
        double admissibilityRate = total > 0 ? (recevables * 100.0) / total : 0.0;

        long referredToJustice = investigationRepository.countByStatus()
                .stream()
                .filter(row -> "ARCHIVED".equals(row[0].toString()))
                .mapToLong(row -> (Long) row[1])
                .sum();

        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("dd/MM/yyyy")
                .withZone(ZoneId.of("Africa/Ouagadougou"));
        String period = fmt.format(start) + " — " + fmt.format(end);

        return StatistiqueResponse.builder()
                .totalDossiers(total)
                .countByStatus(byStatus)
                .countBySubmissionMode(byMode)
                .countByType(byType)
                .totalEstimatedLoss(totalLoss)
                .avgRegistrationDelayDays(avgProcessingDays)
                .avgInvestigationDurationDays(avgInvestigationDays)
                .overdueAcknowledgments(overdueAck)
                .overdueInvestigations(overdueInv)
                .admissibilityRate(admissibilityRate)
                .referredToJustice(referredToJustice)
                .period(period)
                .generatedAt(Instant.now().toString())
                .build();
    }

    @Override
    public StatistiqueResponse getQuarterlyStats(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        int endMonth   = startMonth + 2;

        Instant start = Instant.parse(String.format("%d-%02d-01T00:00:00Z", year, startMonth));
        Instant end   = Instant.parse(String.format("%d-%02d-01T00:00:00Z",
                endMonth == 12 ? year + 1 : year,
                endMonth == 12 ? 1 : endMonth + 1));

        log.info("Stats trimestrielles T{} {} : {} → {}", quarter, year, start, end);
        return getDashboard(start, end);
    }

    @Override
    public StatistiqueResponse getAnnualStats(int year) {
        Instant start = Instant.parse(year + "-01-01T00:00:00Z");
        Instant end   = Instant.parse((year + 1) + "-01-01T00:00:00Z");

        log.info("Stats annuelles {} : {} → {}", year, start, end);
        return getDashboard(start, end);
    }

    // ── Stats publiques — mêmes définitions que la liste dossiers ──

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

        // ← Map.of() avec les 6 clés
        return Map.of(
                "totalDossiers",    total,
                "dossiersNouveaux", nouveaux,
                "dossiersEnCours",  enCours,
                "dossiersTraites",  traites,
                "confidentiel",     "100%",
                "delaiJours",       7
        );
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