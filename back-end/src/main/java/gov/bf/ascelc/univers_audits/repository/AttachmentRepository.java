package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository
        extends JpaRepository<Attachment, UUID> {


    List<Attachment> findByDossierId(UUID dossierId);


    List<Attachment> findByDossierIdAndStatus(
            UUID dossierId, AttachmentStatus status);


    List<Attachment> findByInvestigationId(UUID investigationId);


}