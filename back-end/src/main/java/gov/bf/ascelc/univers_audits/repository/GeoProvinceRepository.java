package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.GeoProvince;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GeoProvinceRepository extends JpaRepository<GeoProvince, UUID> {
}
