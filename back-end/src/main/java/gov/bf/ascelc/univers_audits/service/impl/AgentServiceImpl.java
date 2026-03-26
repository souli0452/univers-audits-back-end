package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.AgentMapper;
import gov.bf.ascelc.univers_audits.model.dto.AgentDto;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.service.AgentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;
    private final AgentMapper agentMapper;

    @Override
    @Transactional
    public AgentDto create(AgentDto agentDto) {
        Agent agent = agentMapper.toEntity(agentDto);
        agent.setId(null);

        Agent savedAgent = agentRepository.save(agent);
        return agentMapper.toDto(savedAgent);
    }

    @Override
    public List<AgentDto> findAll() {
        return agentMapper.toDtos(agentRepository.findAll());
    }

    @Override
    public Optional<AgentDto> findById(UUID id) {
        return agentRepository.findById(id).map(agentMapper::toDto);
    }

    @Override
    @Transactional
    public AgentDto update(UUID id, AgentDto agentDto) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        agentMapper.updateEntityFromDto(agentDto, agent);
        agentRepository.save(agent);
        return agentMapper.toDto(agent);
    }

    @Override
    public void delete(UUID id) {
        agentRepository.deleteById(id);
    }
}
