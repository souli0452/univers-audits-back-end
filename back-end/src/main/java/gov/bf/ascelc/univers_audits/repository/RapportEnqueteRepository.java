package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RapportEnqueteRepository extends JpaRepository<RapportEnquete, UUID> {
    Optional<RapportEnquete> findByInvestigationId(UUID investigationId);
}
