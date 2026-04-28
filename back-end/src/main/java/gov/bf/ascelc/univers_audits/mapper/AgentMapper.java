package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.AgentSummaryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AgentMapper {

    @Mapping(target = "departementLabel", expression = "java(mapDepartement(agent))")
    AgentSummaryResponse toSummaryResponse(Agent agent);

    default String mapDepartement(Agent agent) {
        if (agent == null || agent.getDepartement() == null) {
            return null;
        }
        return agent.getDepartement().getLibelle();
    }
}