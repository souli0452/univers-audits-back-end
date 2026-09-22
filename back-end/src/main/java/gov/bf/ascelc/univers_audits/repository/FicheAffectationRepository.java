package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FicheAffectationRepository extends JpaRepository<FicheAffectation, UUID> {
    Optional<FicheAffectation> findByDossierId(UUID dossierId);
    boolean existsByDossierId(UUID dossierId);
}
