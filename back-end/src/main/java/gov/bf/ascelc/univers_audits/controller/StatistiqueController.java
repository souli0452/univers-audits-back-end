package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.model.dto.response.ActeurEtapeDepassementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.ActeurDepassementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PublicationTrimestrielleResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatistiqueResponse;
import gov.bf.ascelc.univers_audits.service.PublicationTrimestrielleService;
import gov.bf.ascelc.univers_audits.service.StatistiqueService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import gov.bf.ascelc.univers_audits.service.DepassementEtapeService;
import gov.bf.ascelc.univers_audits.service.RapportAnnuelPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.STATS)
public class StatistiqueController {

    private final StatistiqueService statistiqueService;
    private final PublicationTrimestrielleService publicationTrimestrielleService;
    private final RapportAnnuelPdfService rapportAnnuelPdfService;
    private final DepassementEtapeService depassementEtapeService;

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> getPublicStats() {
        return ResponseEntity.ok(statistiqueService.getPublicStats());
    }

    /**
     * Statistiques globales (tous les dossiers) : réservées à la direction (CGE, CGEA), à l'administration et à la
     * communication (DCP). Les autres agents ne voient que leurs propres dossiers, jamais des totaux globaux.
     */
    @GetMapping("/dashboard")
    @HasPermission(AppPermission.STATISTIQUE_GLOBALE_VOIR)
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

    /**
     * « Situation des plaintes et dénonciations » d'un trimestre (T1 à T4), d'un semestre (S1, S2) ou de l'année
     * (ANNEE), telle que publiée sur le site : huit tableaux
     * d'agrégats sans donnée personnelle (mêmes droits que le tableau de bord global).
     */
    @GetMapping("/publication")
    @HasPermission(AppPermission.STATISTIQUE_GLOBALE_VOIR)
    public ResponseEntity<PublicationTrimestrielleResponse> getPublicationTrimestrielle(
            @RequestParam int year,
            @RequestParam String period) {

        log.info("Situation publiée — {} {}", period, year);
        return ResponseEntity.ok(publicationTrimestrielleService.situation(year, period));
    }

    @GetMapping("/depassements-etapes")
    @HasPermission(AppPermission.STATISTIQUE_DEPASSEMENTS_VOIR)
    public ResponseEntity<List<ActeurEtapeDepassementResponse>> getDepassementsEtapes() {
        log.info("Tableau des étapes du circuit en retard par acteur");
        return ResponseEntity.ok(depassementEtapeService.parActeur());
    }

    @GetMapping("/depassements-par-acteur")
    @HasPermission(AppPermission.STATISTIQUE_DEPASSEMENTS_VOIR)
    public ResponseEntity<List<ActeurDepassementResponse>> getDepassementsParActeur() {
        log.info("Tableau des dossiers en dépassement par acteur");
        return ResponseEntity.ok(statistiqueService.getDepassementsParActeur());
    }

    @GetMapping("/quarterly")
    @HasPermission(AppPermission.STATISTIQUE_GLOBALE_VOIR)
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
    @HasPermission(AppPermission.STATISTIQUE_GLOBALE_VOIR)
    public ResponseEntity<StatistiqueResponse> getAnnualStats(
            @RequestParam int year) {

        if (year < 2020 || year > 2100) {
            throw new BusinessException("Année invalide : " + year);
        }
        log.info("Stats annuelles {}", year);
        return ResponseEntity.ok(statistiqueService.getAnnualStats(year));
    }

    @GetMapping("/annual/export")
    @HasPermission(AppPermission.STATISTIQUE_EXPORTER)
    public ResponseEntity<byte[]> exportAnnualStats(
            @RequestParam int year) {

        if (year < 2020 || year > 2100) {
            throw new BusinessException("Année invalide : " + year);
        }
        log.info("Export PDF rapport annuel {}", year);
        byte[] pdf = rapportAnnuelPdfService.exportRapportAnnuel(year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport-annuel-activite-" + year + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
