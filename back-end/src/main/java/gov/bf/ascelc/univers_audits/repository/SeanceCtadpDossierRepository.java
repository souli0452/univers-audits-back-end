package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpDossierRepository
        extends JpaRepository<SeanceCtadpDossier, UUID> {

    boolean existsBySeanceCtadpIdAndDossierId(UUID seanceCtadpId, UUID dossierId);

    Optional<SeanceCtadpDossier> findBySeanceCtadpIdAndDossierId(
            UUID seanceCtadpId, UUID dossierId);
}
