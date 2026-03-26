package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.AgentDto;
import gov.bf.ascelc.univers_audits.service.AgentService;
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
@RequestMapping(ApiUrls.AGENTS)
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public ResponseEntity<AgentDto> create(@Valid @RequestBody AgentDto agentDto) {
        AgentDto createdAgent = agentService.create(agentDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAgent);
    }
}
