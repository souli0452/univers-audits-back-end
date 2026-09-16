package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FicheRetexRepository extends JpaRepository<FicheRetex, UUID> {

    Optional<FicheRetex> findByInvestigationId(UUID investigationId);
}
