package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionCGERepository extends JpaRepository<DecisionCGE, UUID> {

    Optional<DecisionCGE> findByDossierId(UUID dossierId);
}
