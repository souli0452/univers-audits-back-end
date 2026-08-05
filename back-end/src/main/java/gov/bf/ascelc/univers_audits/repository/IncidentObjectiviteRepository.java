package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.IncidentObjectivite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentObjectiviteRepository
        extends JpaRepository<IncidentObjectivite, UUID> {

    List<IncidentObjectivite> findByInvestigationIdOrderByDeclaredAtDesc(
            UUID investigationId);
}
