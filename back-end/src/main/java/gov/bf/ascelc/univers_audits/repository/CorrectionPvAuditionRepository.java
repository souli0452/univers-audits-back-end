package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.CorrectionPvAudition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CorrectionPvAuditionRepository extends JpaRepository<CorrectionPvAudition, UUID> {

    List<CorrectionPvAudition> findByPvAuditionIdOrderByVersionNumberAsc(UUID pvAuditionId);
}
