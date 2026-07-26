package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeDocumentsRepository extends JpaRepository<DemandeDocuments, UUID> {

    List<DemandeDocuments> findByInvestigationIdOrderBySentAtDesc(UUID investigationId);
}
