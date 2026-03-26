package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.AttachmentDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentService {
    AttachmentDto create(AttachmentDto attachmentDto);

    List<AttachmentDto> findAll();

    Optional<AttachmentDto> findById(UUID id);

    AttachmentDto update(UUID id, AttachmentDto attachmentDto);

    void delete(UUID id);
}
