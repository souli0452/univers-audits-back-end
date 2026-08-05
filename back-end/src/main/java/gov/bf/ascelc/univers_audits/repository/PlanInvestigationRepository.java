package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PlanInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanInvestigationRepository
        extends JpaRepository<PlanInvestigation, UUID> {

    Optional<PlanInvestigation> findByInvestigationId(UUID investigationId);
}
