package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.service.InventairePiecesService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class InventairePiecesController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";

    private final InventairePiecesService inventairePiecesService;

    @GetMapping("/inventaire-pieces")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<InventairePieceItemResponse>> getInventaire(
            @PathVariable UUID id) {
        return ResponseEntity.ok(inventairePiecesService.getInventaire(id));
    }
}
