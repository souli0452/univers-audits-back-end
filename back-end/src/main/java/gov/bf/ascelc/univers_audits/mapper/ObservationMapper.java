package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.ObservationDto;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ObservationMapper {

    ObservationDto toDto(Observation observation);

    Observation toEntity(ObservationDto observationDto);

    List<ObservationDto> toDtos(List<Observation> observations);

    List<Observation> toEntities(List<ObservationDto> observationDtos);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(ObservationDto observationDto, @MappingTarget Observation observation);
}
