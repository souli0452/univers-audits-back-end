package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.GeoRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GeoRegionRepository extends JpaRepository<GeoRegion, UUID> {
}
