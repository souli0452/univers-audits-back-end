package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.DeclarantDto;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface DeclarantMapper {

    DeclarantDto toDto(Declarant declarant);

    Declarant toEntity(DeclarantDto declarantDto);

    List<DeclarantDto> toDtos(List<Declarant> declarants);

    List<Declarant> toEntities(List<DeclarantDto> declarantDtos);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(DeclarantDto declarantDto, @MappingTarget Declarant declarant);
}
