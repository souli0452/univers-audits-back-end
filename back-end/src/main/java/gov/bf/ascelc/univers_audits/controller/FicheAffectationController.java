package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheAffectationResponse;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.service.FicheAffectationService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.DOSSIERS + "/{id}/fiche-affectation")
public class FicheAffectationController {

    private final FicheAffectationService ficheAffectationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<FicheAffectationResponse> creer(
            @PathVariable UUID id,
            @Valid @RequestBody FicheAffectationCreateRequest request) {

        log.info("Création fiche d'affectation — dossier {}", id);
        FicheAffectation fiche = ficheAffectationService.creer(id, request);
        return ResponseEntity.status(201).body(toResponse(fiche));
    }

    @PatchMapping("/affectation")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<FicheAffectationResponse> affecter(
            @PathVariable UUID id,
            @Valid @RequestBody FicheAffectationAffectationRequest request) {

        log.info("Affectation CGEA — dossier {}, type {}", id, request.getTypeDesignation());
        FicheAffectation fiche = ficheAffectationService.affecter(id, request);
        return ResponseEntity.ok(toResponse(fiche));
    }

    @PatchMapping("/suivi")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FicheAffectationResponse> suivre(
            @PathVariable UUID id,
            @Valid @RequestBody FicheAffectationSuiviRequest request) {

        log.info("Suivi fiche d'affectation — dossier {}, état {}", id, request.getEtatAvancement());
        FicheAffectation fiche = ficheAffectationService.suivre(id, request);
        return ResponseEntity.ok(toResponse(fiche));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FicheAffectationResponse> get(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(ficheAffectationService.getOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            // Patron identique à RapportEnqueteController.getRapport : l'absence
            // de fiche pour ce dossier est un état normal (pas encore créée par
            // le CGE), pas une erreur — 204 plutôt que 404.
            return ResponseEntity.noContent().build();
        }
    }

    private FicheAffectationResponse toResponse(FicheAffectation f) {
        return FicheAffectationResponse.builder()
                .id(f.getId())
                .dossierId(f.getDossier().getId())
                .decisionCge(f.getDecisionCge())
                .observationsCge(f.getObservationsCge())
                .agentCgeNom(f.getAgentCge() != null ? f.getAgentCge().getNomComplet() : null)
                .dateDecisionCge(f.getDateDecisionCge())
                .typeDesignation(f.getTypeDesignation())
                .departementDesigneId(f.getDepartementDesigne() != null ? f.getDepartementDesigne().getId() : null)
                .departementDesigneLibelle(f.getDepartementDesigne() != null ? f.getDepartementDesigne().getLibelle() : null)
                .agentDesigneId(f.getAgentDesigne() != null ? f.getAgentDesigne().getId() : null)
                .agentDesigneNom(f.getAgentDesigne() != null ? f.getAgentDesigne().getNomComplet() : null)
                .observationsCgea(f.getObservationsCgea())
                .agentCgeaNom(f.getAgentCgea() != null ? f.getAgentCgea().getNomComplet() : null)
                .dateImputation(f.getDateImputation())
                .dateRetour(f.getDateRetour())
                .etatAvancement(f.getEtatAvancement())
                .etatAvancementPrecision(f.getEtatAvancementPrecision())
                .commentairesSuivi(f.getCommentairesSuivi())
                .agentSuiviNom(f.getAgentSuivi() != null ? f.getAgentSuivi().getNomComplet() : null)
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
