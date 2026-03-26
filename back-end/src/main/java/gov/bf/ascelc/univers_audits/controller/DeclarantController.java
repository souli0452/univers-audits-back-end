package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.DeclarantDto;
import gov.bf.ascelc.univers_audits.service.DeclarantService;
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
@RequestMapping("/api/declarants")
public class DeclarantController {

    private final DeclarantService declarantService;

    @PostMapping
    public ResponseEntity<DeclarantDto> create(@Valid @RequestBody DeclarantDto declarantDto) {
        DeclarantDto createdDeclarant = declarantService.create(declarantDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDeclarant);
    }
}
