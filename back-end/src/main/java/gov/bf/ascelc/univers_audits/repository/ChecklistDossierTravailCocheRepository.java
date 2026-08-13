package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ChecklistDossierTravailCoche;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChecklistDossierTravailCocheRepository
        extends JpaRepository<ChecklistDossierTravailCoche, UUID> {
    List<ChecklistDossierTravailCoche> findByInvestigationId(UUID investigationId);
    Optional<ChecklistDossierTravailCoche> findByInvestigationIdAndPointId(
            UUID investigationId, UUID pointId);
    long countByInvestigationIdAndCocheTrue(UUID investigationId);
    long countByInvestigationIdAndCocheTrueAndPointActifTrue(UUID investigationId);
}
