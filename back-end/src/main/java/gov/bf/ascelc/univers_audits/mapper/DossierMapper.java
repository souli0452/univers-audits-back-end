package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AgentSummaryResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationSummaryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DossierMapper {

    @Mapping(target = "acknowledgmentOverdue", ignore = true)
    @Mapping(target = "daysSinceReception", ignore = true)
    DossierResponse toResponse(Dossier dossier);

    @AfterMapping
    default void fillCalculatedFields(
            Dossier dossier,
            @MappingTarget DossierResponse response) {
        response.setAcknowledgmentOverdue(dossier.isAcknowledgmentOverdue());
        response.setDaysSinceReception(dossier.getDaysSinceReception());
    }

    @Mapping(target = "isConfidential", source = "isConfidential")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "number", ignore = true)
    @Mapping(target = "accessCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "declarant", ignore = true)
    @Mapping(target = "agentInCharge", ignore = true)
    @Mapping(target = "targetedParties", ignore = true)
    @Mapping(target = "witnesses", ignore = true)
    @Mapping(target = "observations", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "statusHistory", ignore = true)
    @Mapping(target = "investigation", ignore = true)
    @Mapping(target = "receptionDate", ignore = true)
    @Mapping(target = "acknowledgmentDeadline", ignore = true)
    @Mapping(target = "additionalInfoDeadline", ignore = true)
    @Mapping(target = "eligibilityDecisionDate", ignore = true)
    @Mapping(target = "transferDate", ignore = true)
    @Mapping(target = "transferInstitution", ignore = true)
    @Mapping(target = "closingDate", ignore = true)
    Dossier toEntity(DossierCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "isConfidential", source = "isConfidential")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "number", ignore = true)
    @Mapping(target = "accessCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "submissionMode", ignore = true)
    @Mapping(target = "declarant", ignore = true)
    @Mapping(target = "agentInCharge", ignore = true)
    @Mapping(target = "targetedParties", ignore = true)
    @Mapping(target = "witnesses", ignore = true)
    @Mapping(target = "observations", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "statusHistory", ignore = true)
    @Mapping(target = "investigation", ignore = true)
    @Mapping(target = "receptionDate", ignore = true)
    @Mapping(target = "acknowledgmentDeadline", ignore = true)
    @Mapping(target = "additionalInfoDeadline", ignore = true)
    @Mapping(target = "eligibilityDecisionDate", ignore = true)
    @Mapping(target = "transferDate", ignore = true)
    @Mapping(target = "closingDate", ignore = true)
    void updateEntity(DossierUpdateRequest request, @MappingTarget Dossier dossier);

    @Mapping(
            target = "departementLabel",
            expression = "java(agent.getDepartement() != null ? agent.getDepartement().getLibelle() : null)"
    )
    AgentSummaryResponse toSummaryResponse(Agent agent);

    @Mapping(target = "memberCount", ignore = true)
    InvestigationSummaryResponse toSummaryResponse(Investigation investigation);
}