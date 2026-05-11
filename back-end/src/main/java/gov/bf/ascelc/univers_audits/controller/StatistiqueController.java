package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.service.StatistiqueService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.STATS)
public class StatistiqueController {

    private final StatistiqueService statistiqueService;


    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> getPublicStats() {
        return ResponseEntity.ok(statistiqueService.getPublicStats());
    }



    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StatistiqueResponse> getDashboard(
            @RequestParam Instant start,
            @RequestParam Instant end) {

        log.info("Tableau de bord — {} → {}", start, end);
        if (end.isBefore(start)) {
            throw new BusinessException(
                    "La date de fin doit être postérieure à la date de début");
        }
        return ResponseEntity.ok(statistiqueService.getDashboard(start, end));
    }


    @GetMapping("/quarterly")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<StatistiqueResponse> getQuarterlyStats(
            @RequestParam int year,
            @RequestParam int quarter) {

        if (quarter < 1 || quarter > 4) {
            throw new BusinessException(
                    "Le trimestre doit être compris entre 1 et 4. Valeur reçue : " + quarter);
        }
        if (year < 2020 || year > 2100) {
            throw new BusinessException("Année invalide : " + year);
        }
        log.info("Stats trimestrielles T{} {}", quarter, year);
        return ResponseEntity.ok(statistiqueService.getQuarterlyStats(year, quarter));
    }

    @GetMapping("/annual")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<StatistiqueResponse> getAnnualStats(
            @RequestParam int year) {

        if (year < 2020 || year > 2100) {
            throw new BusinessException("Année invalide : " + year);
        }
        log.info("Stats annuelles {}", year);
        return ResponseEntity.ok(statistiqueService.getAnnualStats(year));
    }
}