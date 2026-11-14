package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.InventairePiecesService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class InventairePiecesController {

    private final InventairePiecesService inventairePiecesService;

    @GetMapping("/inventaire-pieces")
    @HasPermission(AppPermission.INVENTAIRE_PIECES_VOIR)
    public ResponseEntity<List<InventairePieceItemResponse>> getInventaire(
            @PathVariable UUID id) {
        return ResponseEntity.ok(inventairePiecesService.getInventaire(id));
    }
}
