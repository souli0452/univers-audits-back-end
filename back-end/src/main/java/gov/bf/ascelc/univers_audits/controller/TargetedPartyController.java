package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.TargetedPartyDto;
import gov.bf.ascelc.univers_audits.service.TargetedPartyService;
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
@RequestMapping(ApiUrls.TARGETEDPARTIES)
public class TargetedPartyController {

    private final TargetedPartyService targetedPartyService;

    @PostMapping
    public ResponseEntity<TargetedPartyDto> create(@Valid @RequestBody TargetedPartyDto targetedPartyDto) {
        TargetedPartyDto createdTargetedParty = targetedPartyService.create(targetedPartyDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTargetedParty);
    }
}
