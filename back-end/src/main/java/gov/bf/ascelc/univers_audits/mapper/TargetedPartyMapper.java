package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.TargetedPartyDto;
import gov.bf.ascelc.univers_audits.model.entity.TargetedParty;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TargetedPartyMapper {

    TargetedPartyDto toDto(TargetedParty targetedParty);

    TargetedParty toEntity(TargetedPartyDto targetedPartyDto);

    List<TargetedPartyDto> toDtos(List<TargetedParty> targetedParties);

    List<TargetedParty> toEntities(List<TargetedPartyDto> targetedPartyDtos);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(TargetedPartyDto targetedPartyDto, @MappingTarget TargetedParty targetedParty);
}
