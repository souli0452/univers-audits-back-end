package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Audition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditionRepository extends JpaRepository<Audition, UUID> {

    List<Audition> findByInvestigationIdOrderByScheduledAtAsc(UUID investigationId);
}
