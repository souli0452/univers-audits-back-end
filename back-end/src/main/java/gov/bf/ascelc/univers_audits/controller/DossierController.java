package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.DossierDto;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.DOSSIERS)
public class DossierController {

    private final DossierService dossierService;

    @PostMapping
    public ResponseEntity<DossierDto> create(@Valid @RequestBody DossierDto dossierDto) {
        DossierDto createdDossier = dossierService.create(dossierDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDossier);
    }
}
