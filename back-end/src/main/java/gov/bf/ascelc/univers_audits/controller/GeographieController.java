package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.GeographieResponse;
import gov.bf.ascelc.univers_audits.service.GeographieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class GeographieController {

    private final GeographieService geographieService;

    /**
     * Pays → Région → Province → Commune, pour les listes déroulantes du formulaire de dépôt. Public : c'est un
     * référentiel administratif, sans donnée personnelle ; il ne change qu'avec une mise à jour de l'application.
     */
    @GetMapping("/api/v1/public/geographie")
    public ResponseEntity<GeographieResponse> arbre() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(geographieService.arbre());
    }
}
