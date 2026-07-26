package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PVAuditionRepository extends JpaRepository<PVAudition, UUID> {

    Optional<PVAudition> findByAuditionId(UUID auditionId);
}
