package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.AttachmentDto;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AttachmentMapper {

    AttachmentDto toDto(Attachment attachment);

    Attachment toEntity(AttachmentDto attachmentDto);

    List<AttachmentDto> toDtos(List<Attachment> attachments);

    List<Attachment> toEntities(List<AttachmentDto> attachmentDtos);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(AttachmentDto attachmentDto, @MappingTarget Attachment attachment);
}
