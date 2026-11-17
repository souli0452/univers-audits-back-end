package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.GeoCommune;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GeoCommuneRepository extends JpaRepository<GeoCommune, UUID> {
}
