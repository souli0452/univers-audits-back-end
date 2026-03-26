package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.AgentDto;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AgentMapper {

    AgentDto toDto(Agent agent);

    Agent toEntity(AgentDto agentDto);

    List<AgentDto> toDtos(List<Agent> agents);

    List<Agent> toEntities(List<AgentDto> agentDtos);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(AgentDto agentDto, @MappingTarget Agent agent);
}
