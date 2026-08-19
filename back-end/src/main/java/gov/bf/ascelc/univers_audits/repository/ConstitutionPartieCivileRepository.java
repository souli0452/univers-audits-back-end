package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ConstitutionPartieCivile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConstitutionPartieCivileRepository extends JpaRepository<ConstitutionPartieCivile, UUID> {
    Optional<ConstitutionPartieCivile> findByInvestigationId(UUID investigationId);
}
