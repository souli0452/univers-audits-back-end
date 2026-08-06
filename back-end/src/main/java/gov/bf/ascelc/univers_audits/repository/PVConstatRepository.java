package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PVConstat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PVConstatRepository extends JpaRepository<PVConstat, UUID> {

    Optional<PVConstat> findByVisiteTerrainId(UUID visiteTerrainId);
}
