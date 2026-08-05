package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
import gov.bf.ascelc.univers_audits.model.entity.ProcedureUrgence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcedureUrgenceRepository extends JpaRepository<ProcedureUrgence, UUID> {

    List<ProcedureUrgence> findByInvestigationIdOrderByRequestedAtDesc(UUID investigationId);

    Optional<ProcedureUrgence> findByIdAndInvestigationId(UUID id, UUID investigationId);

    boolean existsByInvestigationIdAndStatus(UUID investigationId, StatutProcedureUrgence status);
}
