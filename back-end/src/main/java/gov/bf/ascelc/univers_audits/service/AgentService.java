package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.AgentDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentService {
    AgentDto create(AgentDto agentDto);

    List<AgentDto> findAll();

    Optional<AgentDto> findById(UUID id);

    AgentDto update(UUID id, AgentDto agentDto);

    void delete(UUID id);
}
