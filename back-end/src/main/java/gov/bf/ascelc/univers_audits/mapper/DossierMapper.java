package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.DossierDto;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface DossierMapper {

    DossierDto toDto(Dossier dossier);

    Dossier toEntity(DossierDto dossierDto);

    List<DossierDto> toDtos(List<Dossier> dossiers);

    List<Dossier> toEntities(List<DossierDto> dossierDtos);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto (DossierDto dossierDto, @MappingTarget Dossier dossier);
}

