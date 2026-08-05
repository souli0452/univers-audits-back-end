package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RevisionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RevisionPlanRepository extends JpaRepository<RevisionPlan, UUID> {

    List<RevisionPlan> findByPlanInvestigationIdOrderByVersionNumberDesc(
            UUID planInvestigationId);
}
