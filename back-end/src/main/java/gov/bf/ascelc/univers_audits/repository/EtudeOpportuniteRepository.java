package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtudeOpportuniteRepository
        extends JpaRepository<EtudeOpportunite, UUID> {

    Optional<EtudeOpportunite> findByDossierId(UUID dossierId);

    boolean existsByDossierId(UUID dossierId);
}
