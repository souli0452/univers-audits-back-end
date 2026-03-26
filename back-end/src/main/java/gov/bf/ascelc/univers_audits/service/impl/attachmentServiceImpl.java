package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.AttachmentMapper;
import gov.bf.ascelc.univers_audits.model.dto.AttachmentDto;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.service.AttachmentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class attachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;

    @Override
    @Transactional
    public AttachmentDto create(AttachmentDto attachmentDto) {
        Attachment attachment = attachmentMapper.toEntity(attachmentDto);
        attachment.setId(null);

        Attachment savedAttachment = attachmentRepository.save(attachment);
        return attachmentMapper.toDto(savedAttachment);
    }

    @Override
    public List<AttachmentDto> findAll() {
        return attachmentMapper.toDtos(attachmentRepository.findAll());
    }

    @Override
    public Optional<AttachmentDto> findById(UUID id) {
        return attachmentRepository.findById(id).map(attachmentMapper::toDto);
    }

    @Override
    @Transactional
    public AttachmentDto update(UUID id, AttachmentDto attachmentDto) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found"));
        attachmentMapper.updateEntityFromDto(attachmentDto, attachment);
        attachmentRepository.save(attachment);
        return attachmentMapper.toDto(attachment);
    }

    @Override
    public void delete(UUID id) {
        attachmentRepository.deleteById(id);
    }
}
