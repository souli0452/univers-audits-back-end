package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.WitnessDto;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface WitnessMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    WitnessDto toDto(Witness witness);

    @Mapping(source = "dossierId", target = "dossier.id")
    Witness toEntity(WitnessDto witnessDto);

    List<WitnessDto> toDtos(List<Witness> witnesses);

    List<Witness> toEntities(List<WitnessDto> witnessDtos);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "dossierId", target = "dossier.id")
    void updateEntityFromDto(WitnessDto witnessDto, @MappingTarget Witness witness);
}
