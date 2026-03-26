package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.ObservationDto;
import gov.bf.ascelc.univers_audits.service.ObservationService;
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
@RequestMapping(ApiUrls.OBSERVATIONS)
public class ObservationController {
    private final ObservationService observationService;
    @PostMapping
    public ResponseEntity<ObservationDto> create(@Valid @RequestBody ObservationDto observationDto){
        ObservationDto createdObservation = observationService.create(observationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdObservation);
    }
}
