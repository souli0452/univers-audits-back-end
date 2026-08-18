package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PlanActions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanActionsRepository extends JpaRepository<PlanActions, UUID> {
    Optional<PlanActions> findByInvestigationId(UUID investigationId);
}
