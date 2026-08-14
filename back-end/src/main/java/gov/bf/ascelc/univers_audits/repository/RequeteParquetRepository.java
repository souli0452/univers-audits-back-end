package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RequeteParquetRepository extends JpaRepository<RequeteParquet, UUID> {
    Optional<RequeteParquet> findByInvestigationId(UUID investigationId);
}
