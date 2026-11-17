package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.NoteRecommandationsResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RapportEnqueteResponse;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.RapportEnqueteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class RapportEnqueteController {

    private final RapportEnqueteService rapportEnqueteService;

    @GetMapping("/rapport")
    @HasPermission(AppPermission.RAPPORT_ENQUETE_VOIR)
    public ResponseEntity<RapportEnqueteResponse> getRapport(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(rapportEnqueteService.getRapportOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/rapport")
    @HasPermission(AppPermission.RAPPORT_ENQUETE_REDIGER)
    public ResponseEntity<RapportEnqueteResponse> putRapport(
            @PathVariable UUID id,
            @Valid @RequestBody RapportEnqueteRequest request) {

        log.info("Enregistrement rapport d'enquête — investigation {}", id);
        RapportEnquete saved = rapportEnqueteService.enregistrerRapport(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/note-recommandations")
    @HasPermission(AppPermission.RAPPORT_ENQUETE_VOIR)
    public ResponseEntity<NoteRecommandationsResponse> getNote(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(rapportEnqueteService.getNoteOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/note-recommandations")
    @HasPermission(AppPermission.RAPPORT_ENQUETE_REDIGER)
    public ResponseEntity<NoteRecommandationsResponse> putNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRecommandationsRequest request) {

        log.info("Enregistrement note de recommandations — investigation {}", id);
        NoteRecommandations saved = rapportEnqueteService.enregistrerNote(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    private RapportEnqueteResponse toResponse(RapportEnquete r) {
        return RapportEnqueteResponse.builder()
                .id(r.getId())
                .investigationId(r.getInvestigation().getId())
                .titre(r.getTitre())
                .introduction(r.getIntroduction())
                .methodologie(r.getMethodologie())
                .informationsCollectees(r.getInformationsCollectees())
                .exposeFactuelAnomalies(r.getExposeFactuelAnomalies())
                .quantificationPrejudice(r.getQuantificationPrejudice())
                .reserves(r.getReserves())
                .conclusions(r.getConclusions())
                .complet(r.isComplet())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private NoteRecommandationsResponse toResponse(NoteRecommandations n) {
        return NoteRecommandationsResponse.builder()
                .id(n.getId())
                .rapportEnqueteId(n.getRapportEnquete().getId())
                .contenu(n.getContenu())
                .complet(n.isComplet())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
