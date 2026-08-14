package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RequeteParquetResponse;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.service.RequeteParquetService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class RequeteParquetController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')";

    private final RequeteParquetService requeteParquetService;

    @GetMapping("/requete-parquet")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<RequeteParquetResponse> getRequeteParquet(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(requeteParquetService.getOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/requete-parquet")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RequeteParquetResponse> putRequeteParquet(
            @PathVariable UUID id,
            @Valid @RequestBody RequeteParquetRequest request) {

        log.info("Enregistrement requête Parquet — investigation {}", id);
        RequeteParquet saved = requeteParquetService.enregistrer(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    private RequeteParquetResponse toResponse(RequeteParquet r) {
        return RequeteParquetResponse.builder()
                .id(r.getId())
                .investigationId(r.getInvestigation().getId())
                .contenu(r.getContenu())
                .complet(r.isComplet())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
