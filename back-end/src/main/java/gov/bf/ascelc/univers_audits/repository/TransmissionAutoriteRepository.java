package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.TransmissionAutorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransmissionAutoriteRepository extends JpaRepository<TransmissionAutorite, UUID> {
    Optional<TransmissionAutorite> findByInvestigationId(UUID investigationId);
}
