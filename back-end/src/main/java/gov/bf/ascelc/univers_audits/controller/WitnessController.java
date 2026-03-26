package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.WitnessDto;
import gov.bf.ascelc.univers_audits.service.WitnessService;
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
@RequestMapping(ApiUrls.WITNESSES)
public class WitnessController {

    private final WitnessService witnessService;

    @PostMapping
    public ResponseEntity<WitnessDto> create(@Valid @RequestBody WitnessDto witnessDto) {
        WitnessDto createdWitness = witnessService.create(witnessDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWitness);
    }
}
