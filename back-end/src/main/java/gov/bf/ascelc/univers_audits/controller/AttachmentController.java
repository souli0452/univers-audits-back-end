package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.AttachmentDto;
import gov.bf.ascelc.univers_audits.service.AttachmentService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.ATTACHMENTS)
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping
    public ResponseEntity<AttachmentDto> create(@Valid @RequestBody AttachmentDto attachmentDto) {
        AttachmentDto createdAttachment = attachmentService.create(attachmentDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAttachment);
    }
}
