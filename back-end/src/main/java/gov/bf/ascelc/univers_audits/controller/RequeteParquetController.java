package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RequeteParquetResponse;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.RequeteParquetService;
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
public class RequeteParquetController {

    private final RequeteParquetService requeteParquetService;

    @GetMapping("/requete-parquet")
    @HasPermission(AppPermission.REQUETE_PARQUET_VOIR)
    public ResponseEntity<RequeteParquetResponse> getRequeteParquet(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(requeteParquetService.getOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/requete-parquet")
    @HasPermission(AppPermission.REQUETE_PARQUET_REDIGER)
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
