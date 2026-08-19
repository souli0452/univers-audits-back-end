package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SuiviProcedurePenale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuiviProcedurePenaleRepository extends JpaRepository<SuiviProcedurePenale, UUID> {
    List<SuiviProcedurePenale> findByInvestigationIdOrderByPhaseAtDesc(UUID investigationId);
}
