package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        uses = AgentMapper.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DossierHabilitationMapper {

    @Mapping(target = "grantedAt", source = "createdAt")
    @Mapping(target = "active", expression = "java(habilitation.getRevokedAt() == null)")
    DossierHabilitationResponse toResponse(DossierHabilitation habilitation);
}
