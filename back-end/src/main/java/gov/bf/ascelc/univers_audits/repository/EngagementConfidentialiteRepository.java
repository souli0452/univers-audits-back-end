package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.EngagementConfidentialite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngagementConfidentialiteRepository
        extends JpaRepository<EngagementConfidentialite, UUID> {

    Optional<EngagementConfidentialite> findByInvestigationIdAndAgentId(
            UUID investigationId, UUID agentId);
}
