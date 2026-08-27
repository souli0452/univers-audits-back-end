package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.service.LeconAPartagerService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.LECONS_A_PARTAGER)
public class LeconAPartagerController {

    private final LeconAPartagerService leconAPartagerService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<LeconAPartagerResponse>> lister(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(leconAPartagerService.lister(pageable));
    }
}
