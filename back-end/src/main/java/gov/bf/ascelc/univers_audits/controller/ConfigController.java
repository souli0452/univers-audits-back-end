package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.entity.Departement;
import gov.bf.ascelc.univers_audits.model.entity.TypeDeclarantConfig;
import gov.bf.ascelc.univers_audits.repository.DepartementRepository;
import gov.bf.ascelc.univers_audits.repository.TypeDeclarantConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final DepartementRepository         departementRepository;
    private final TypeDeclarantConfigRepository typeDeclarantConfigRepository;


    @GetMapping("/types-declarant")
    public ResponseEntity<List<Map<String, Object>>> getTypesDeclarant() {
        List<Map<String, Object>> result =
                typeDeclarantConfigRepository
                        .findByVisibleOnPublicFormTrueAndActiveTrueOrderByDisplayOrderAsc()
                        .stream()
                        .map(c -> Map.<String, Object>of(
                                "code",               c.getTypeDeclarant().name(),
                                "label",              c.getLabel(),
                                "description",        c.getDescription() != null
                                        ? c.getDescription() : "",
                                "idDocumentRequired", c.getIdDocumentRequired(),
                                "protectionAvailable", c.getProtectionAvailable(),
                                "consentRequired",    c.getConsentRequired()
                        ))
                        .toList();
        return ResponseEntity.ok(result);
    }


    @GetMapping("/departements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getDepartements() {
        List<Map<String, Object>> result =
                departementRepository
                        .findByActifTrueOrderByOrdreAffichageAsc()
                        .stream()
                        .map(d -> Map.<String, Object>of(
                                "id",      d.getId().toString(),
                                "code",    d.getCode(),
                                "libelle", d.getLibelle()
                        ))
                        .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/enums")
    public ResponseEntity<Map<String, Object>> getEnums() {
        return ResponseEntity.ok(Map.of(
                "submissionModes", List.of(
                        "IN_PERSON", "AUDIO_COUNTER", "WEB_FORM",
                        "PAPER_FORM", "EMAIL", "SMS", "PHONE",
                        "GREEN_NUMBER", "SOCIAL_MEDIA", "PRESS_MEDIA",
                        "AUDIT_REPORT", "POSTAL_MAIL"
                ),
                "typeSaisine", List.of(
                        "COMPLAINT", "DENUNCIATION",
                        "AUTO_REFERRAL", "ANONYMOUS"
                ),
                "partyTypes", List.of(
                        "PRIVATE_PERSON", "COMPANY",
                        "PUBLIC_AGENT", "PUBLIC_AUTHORITY"
                ),
                "allegedRoles", List.of(
                        "MAIN_PERPETRATOR", "ACCOMPLICE",
                        "BENEFICIARY", "INSTIGATOR"
                ),
                "observationTypes", List.of(
                        "INTERNAL_NOTE", "ADMISSIBILITY_ANALYSIS",
                        "CTADP_OPINION", "COMPLEMENT_REQUEST",
                        "CGE_DECISION", "FIELD_FINDING",
                        "TRANSFER_NOTE"
                ),
                "dossierStatuses", List.of(
                        "SOUMIS", "RECU", "EN_ETUDE_OPPORTUNITE",
                        "EN_ATTENTE_COMPLEMENT", "EN_REVUE_CTADP",
                        "RECEVABLE", "IRRECEVABLE", "TRANSFERE",
                        "EN_INVESTIGATION", "RAPPORT_PRODUIT",
                        "DECISION_RENDUE", "CLOS", "CLASSE"
                )
        ));
    }
}